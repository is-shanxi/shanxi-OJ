package com.yupi.yuoj.judge.codesandbox;

import com.yupi.yuoj.judge.codesandbox.impl.RemoteCodeSandbox;
import com.yupi.yuoj.judge.codesandbox.model.ExecuteCodeRequest;
import com.yupi.yuoj.judge.codesandbox.model.ExecuteCodeResponse;
import com.yupi.yuoj.model.enums.QuestionSubmitLanguageEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

@SpringBootTest
class CodeSandboxTest {

    @Value("${codesandbox.type:example}")
    private String type;

/**
 * 测试代码沙箱执行功能的测试方法
 * 使用远程代码沙箱执行一段简单的Java代码
 */
    @Test
    void executeCode() {
    // 创建远程代码沙箱实例
        CodeSandbox codeSandbox = new RemoteCodeSandbox();
    // 定义要执行的简单Java代码
        String code = "int main() { }";
    // 设置编程语言为Java
        String language = QuestionSubmitLanguageEnum.JAVA.getValue();
    // 定义代码输入参数列表
        List<String> inputList = Arrays.asList("1 2", "3 4");
    // 构建代码执行请求对象
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)              // 设置要执行的代码
                .language(language)      // 设置编程语言
                .inputList(inputList)    // 设置输入参数
                .build();                // 构建请求对象
    // 执行代码并获取响应
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
    // 断言响应不为空，验证执行是否成功
        Assertions.assertNotNull(executeCodeResponse);
    }

/**
 * 测试通过值执行代码的方法
 * 此方法用于验证代码沙箱能够正确执行代码并返回响应
 */
    @Test
    void executeCodeByValue() {
    // 创建代码沙箱实例，根据指定的类型
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
    // 定义要执行的Java代码，这里是一个简单的空main方法
        String code = "int main() { }";
    // 设置编程语言为Java
        String language = QuestionSubmitLanguageEnum.JAVA.getValue();
    // 定义输入列表，包含两组测试输入
        List<String> inputList = Arrays.asList("1 2", "3 4");
    // 构建执行代码的请求对象，包含代码、语言和输入列表
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();
    // 执行代码并获取响应
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
    // 断言响应不为空，验证执行是否成功
        Assertions.assertNotNull(executeCodeResponse);
    }

/**
 * 测试通过代理执行代码的方法
 * 该测试用例验证了代码沙箱的代理模式是否正常工作
 */
    @Test
    void executeCodeByProxy() {
    // 创建代码沙箱实例，使用工厂模式根据类型创建
        CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
    // 使用代理模式包装代码沙箱实例，用于增强功能
        codeSandbox = new CodeSandboxProxy(codeSandbox);
    // 定义一段Java代码，实现两个整数相加的功能
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        int a = Integer.parseInt(args[0]);\n" +
                "        int b = Integer.parseInt(args[1]);\n" +
                "        System.out.println(\"结果:\" + (a + b));\n" +
                "    }\n" +
                "}";
    // 设置代码语言为Java
        String language = QuestionSubmitLanguageEnum.JAVA.getValue();
    // 定义输入测试用例
        List<String> inputList = Arrays.asList("1 2", "3 4");
    // 构建执行代码的请求对象
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();
    // 执行代码并获取响应
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
    // 验证响应不为空
        Assertions.assertNotNull(executeCodeResponse);
    }


/**
 * 主方法，程序入口点
 * @param args 命令行参数
 */
    public static void main(String[] args) {
        // 创建Scanner对象用于读取用户输入
        Scanner scanner = new Scanner(System.in);
        // 循环读取输入，直到没有更多输入
        while (scanner.hasNext()) {
            // 读取下一个输入作为代码沙箱类型
            String type = scanner.next();
            // 根据类型创建新的代码沙箱实例
            CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type);
            // 定义示例代码
            String code = "int main() { }";
            // 设置编程语言为Java
            String language = QuestionSubmitLanguageEnum.JAVA.getValue();
            // 创建输入列表
            List<String> inputList = Arrays.asList("1 2", "3 4");
            // 构建执行代码请求对象
            ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                    .code(code)
                    .language(language)
                    .inputList(inputList)
                    .build();
            // 执行代码并获取响应
            ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        }
    }
}