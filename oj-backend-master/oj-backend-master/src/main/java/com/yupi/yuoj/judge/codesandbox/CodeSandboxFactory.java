package com.yupi.yuoj.judge.codesandbox;

import com.yupi.yuoj.judge.codesandbox.impl.ExampleCodeSandbox;
import com.yupi.yuoj.judge.codesandbox.impl.RemoteCodeSandbox;
import com.yupi.yuoj.judge.codesandbox.impl.ThirdPartyCodeSandbox;

/**
 * 代码沙箱工厂（根据字符串参数创建指定的代码沙箱实例）
 */
public class CodeSandboxFactory {

    /**
     * 创建代码沙箱示例
     *
     * @param type 沙箱类型
     * @return
     */
    public static CodeSandbox newInstance(String type) {
        return newInstance(type, 5000, 60000);
    }

    /**
     * 创建代码沙箱示例（远程沙箱支持配置 HTTP 连接/读取超时）
     *
     * @param type            沙箱类型
     * @param connectTimeout  HTTP 连接超时（毫秒）
     * @param readTimeout     HTTP 读取超时（毫秒），需大于题目判题配置的最大执行时限
     * @return
     */
    public static CodeSandbox newInstance(String type, int connectTimeout, int readTimeout) {
        switch (type) {
            case "example":
                return new ExampleCodeSandbox();
            case "remote":
                return new RemoteCodeSandbox(connectTimeout, readTimeout);
            case "thirdParty":
                return new ThirdPartyCodeSandbox();
            default:
                return new ExampleCodeSandbox();
        }
    }
}
