package com.hmdp.service.impl;

import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.hmdp.entity.BlogComments;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {


    public static void main(String[] args) {
        // 建议从环境变量获取 Key，避免硬编码泄露
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null) {
            System.err.println("请设置环境变量 GOOGLE_API_KEY");
            return;
        }

        // 1. 初始化客户端 (推荐使用 builder 模式显式传入 Key)
        try (Client client = Client.builder().apiKey(apiKey).build()) {

            // 2. 构建配置对象 (拓展部分)
            // 这里可以设置温度、TopP、TopK、输出Token限制等
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .temperature(0.7f) // 控制随机性 (0.0-2.0)
                    .maxOutputTokens(1024) // 限制回答长度
                    .topP(0.95f)
                    // 设置系统指令 (System Instruction)，赋予模型人设
                    .systemInstruction(Content.builder()
                            .parts(Collections.singletonList(Part.builder().text("你是一个风趣幽默的技术专家，回答要简洁明了。").build()))
                            .build())
                    .build();

            String modelName = "gemini-2.5-flash"; // 目前可用的是 1.5-flash 或 2.0-flash-exp，2.5 暂未发布，按需修改
            String prompt = "请用流式输出的方式，解释一下什么是 Java 的 Stream API？";

            System.out.println("--- 开始流式生成 (" + modelName + ") ---");

            // 3. 发起流式请求 (generateContent -> generateContentStream)
            ResponseStream<GenerateContentResponse> stream = client.models.generateContentStream(
                    modelName,
                    prompt,
                    config
            );

            // 4. 迭代处理流式响应
            // ServerStream 实现了 Iterable 接口，可以直接用 foreach
            for (GenerateContentResponse response : stream) {
                // 获取当前分块的文本并打印 (不要换行，实现打字机效果)
                String chunk = response.text();
                if (chunk != null) {
                    System.out.print(chunk);
                }
            }

            System.out.println("\n--- 生成结束 ---");

        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
