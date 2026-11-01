package dev.mocklab.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.IOAccess;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 由 Supervisor 创建的脚本工作进程，使用标准输入输出协议并为每次任务创建独立 JS 上下文
 */
public final class Worker {
    /**
     * 工作进程协议编解码器，用户数据通过 JSON 绑定而非拼接为脚本代码
     */
    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * 注入只读 mock 工具并校验 JSON 数据类型，防止循环引用及访问器副作用进入响应
     */
    private static final String PRELUDE =
            """
            const __request = JSON.parse(__requestJson);
            const mock = Object.freeze({
              uuid: () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const r=Math.floor(Math.random()*16);return(c==='x'?r:(r&3|8)).toString(16)}),
              now: () => new Date().toISOString(),
              randomInt: (a,b) => {if(!Number.isSafeInteger(a)||!Number.isSafeInteger(b)||a>b||!Number.isSafeInteger(b-a))throw Error('randomInt 参数须为合法安全整数');return Math.floor(Math.random()*(b-a+1))+a;}
            });
            function __assertJson(v, seen) {
              if(v===null || typeof v==='string' || typeof v==='boolean') return;
              if(typeof v==='number' && Number.isFinite(v)) return;
              if(typeof v!=='object' || seen.has(v)) throw Error('MOCKLAB_CONTRACT:正文包含不支持的类型或循环引用');
              const proto=Object.getPrototypeOf(v);
              if(!Array.isArray(v) && proto!==Object.prototype && proto!==null)throw Error('MOCKLAB_CONTRACT:正文须为 JSON 数据');
              seen.add(v);
              if(Object.getOwnPropertySymbols(v).length)throw Error('MOCKLAB_CONTRACT:正文不支持 Symbol 字段');
              for(const key of Object.keys(v)) {
                const d=Object.getOwnPropertyDescriptor(v,key);
                if(!d || !Object.hasOwn(d,'value'))throw Error('MOCKLAB_CONTRACT:正文不支持访问器属性');
                __assertJson(d.value,seen);
              }
              if(Array.isArray(v))for(let i=0;i<v.length;i++)if(!Object.hasOwn(v,i))throw Error('MOCKLAB_CONTRACT:数组不能包含空槽');
              seen.delete(v);
            }
            """;
    /**
     * 调用同步 main 并校验响应契约，将非字符串正文序列化后交回父进程
     */
    private static final String EPILOGUE =
            """
            if(typeof main!=='function')throw Error('MOCKLAB_CONTRACT:请定义 function main(request, mock)');
            const __r=main(__request,mock);
            if(!__r || Object.getPrototypeOf(__r)!==Object.prototype || typeof __r.then==='function')throw Error('MOCKLAB_CONTRACT:脚本须同步返回普通响应对象');
            if(Object.keys(__r).some(k=>!['status','headers','body'].includes(k)))throw Error('MOCKLAB_CONTRACT:响应包含未知顶层字段');
            if(!Object.hasOwn(__r,'body'))throw Error('MOCKLAB_CONTRACT:响应必须包含 body');
            __assertJson(__r,new Set());
            if(__r.status!==undefined && (!Number.isInteger(__r.status)||__r.status<200||__r.status>599))throw Error('MOCKLAB_CONTRACT:HTTP 状态码无效');
            if(__r.headers!==undefined && (!__r.headers || Array.isArray(__r.headers)||typeof __r.headers!=='object'||Object.values(__r.headers).some(v=>typeof v!=='string')))throw Error('MOCKLAB_CONTRACT:headers 须为字符串键值对象');
            const __body=typeof __r.body==='string'?__r.body:JSON.stringify(__r.body);
            JSON.stringify({status:__r.status,headers:__r.headers,body:__body});
            """;

    /**
     * 预热 GraalJS 后逐行执行父进程任务，标准输出只用于 READY 和协议响应
     *
     * @param args 预留启动参数，由 Supervisor 启动时传入空数组
     * @throws Exception 引擎初始化失败或协议输入读取失败，工作进程退出
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        try (Engine engine = Engine.create();
                BufferedReader input =
                        new BufferedReader(
                                new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter output =
                        new PrintWriter(
                                new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true)) {
            try (Context warm = context(engine)) {
                warm.eval("js", "JSON.stringify({ready:true})");
            }
            // 只有引擎预热完成后才允许 Supervisor 将进程加入空闲池
            output.println("READY");
            String line;
            while ((line = Supervisor.readLine(input)) != null)
                output.println(execute(engine, line));
        }
    }

    /**
     * 每次任务创建独立上下文并关闭宿主能力，跨请求仅共享引擎而不共享 JS 全局变量
     *
     * @param engine 当前工作进程内共享的 Graal 引擎
     * @return 由调用方负责关闭的受限 JS 上下文
     */
    private static Context context(Engine engine) {
        return Context.newBuilder("js")
                .engine(engine)
                // 关闭宿主类、文件、网络及进程能力，生产环境还需叠加独立容器边界
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .allowIO(IOAccess.NONE)
                // 用户脚本的输入输出不使用进程协议流，防止 console 输出破坏 JSON 分帧
                .in(InputStream.nullInputStream())
                .out(OutputStream.nullOutputStream())
                .err(OutputStream.nullOutputStream())
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowCreateProcess(false)
                .option("js.ecmascript-version", "2023")
                .build();
    }

    /**
     * 区分仅解析的发布校验与真实执行，将脚本异常转换为可识别的协议错误
     *
     * @param engine 当前工作进程的共享引擎
     * @param line 单行 JSON 任务，包含操作、源码和请求上下文
     * @return 成功结果或错误结果的单行 JSON
     */
    private static String execute(Engine engine, String line) {
        try (Context context = context(engine)) {
            JsonNode command = JSON.readTree(line);
            String code = command.path("code").asText();
            if (code.getBytes(StandardCharsets.UTF_8).length > 65536)
                throw new IllegalArgumentException("脚本超过 64 KiB");
            // 发布校验只解析语法，不执行用户代码，避免发布动作产生脚本副作用
            if ("validate".equals(command.path("operation").asText())) {
                context.parse(Source.newBuilder("js", code, "user-script.js").buildLiteral());
                if (!code.matches("(?s).*\\bfunction\\s+main\\s*\\(.*"))
                    throw new IllegalArgumentException("请定义 function main(request, mock)");
                return "{\"ok\":true}";
            }
            // 请求先序列化为 JSON 再绑定变量，避免把报文字段拼接进可执行源码
            context.getBindings("js")
                    .putMember("__requestJson", JSON.writeValueAsString(command.path("request")));
            String result =
                    context.eval(
                                    Source.newBuilder(
                                                    "js",
                                                    PRELUDE + "\n" + code + "\n" + EPILOGUE,
                                                    "user-script.js")
                                            .buildLiteral())
                            .asString();
            return "{\"ok\":true,\"result\":" + result + "}";
        } catch (Exception error) {
            try {
                return JSON.writeValueAsString(
                        Map.of(
                                "ok",
                                false,
                                "errorCode",
                                error.getMessage() != null
                                                && error.getMessage().contains("MOCKLAB_CONTRACT:")
                                        ? "INVALID_SCRIPT_RESPONSE"
                                        : "SCRIPT_EXECUTION_ERROR",
                                "message",
                                safeMessage(error)));
            // 异常编码本身失败时仍返回合法协议帧，避免父进程一直等待结果
            } catch (Exception encoding) {
                return "{\"ok\":false,\"errorCode\":\"SCRIPT_EXECUTION_ERROR\",\"message\":\"脚本执行失败\"}";
            }
        }
    }

    /**
     * 去除内部契约标记并限制错误文本长度，避免把无界脚本异常传回管理端
     *
     * @param error 解析或执行脚本时抛出的异常
     * @return 可传输的错误摘要，缺少异常消息时使用统一提示
     */
    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message != null) message = message.replace("MOCKLAB_CONTRACT:", "");
        if (message == null) return "脚本执行失败";
        return message.substring(0, Math.min(message.length(), 500));
    }
}
