import dev.langchain4j.data.document.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static java.util.stream.Collectors.joining;

public class _04_Retrieval {

    private static String RETRIEVER_DOCUMENT_NAME = "news.pdf";

    public static void main(String[] args) {

        var openAiKey = System.getenv("OPENAI_API_KEY");

        var chatModel = OpenAiChatModel.withApiKey(openAiKey);
        var chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        var embeddingModel = OpenAiEmbeddingModel.withApiKey(openAiKey);
        var embeddingStore = new InMemoryEmbeddingStore<TextSegment>();
        var retriever = EmbeddingStoreRetriever.from(embeddingStore, embeddingModel);

        // #1 - Ingesting the document and store in vectorized form
        var ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 0))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        var filePath = toPath(RETRIEVER_DOCUMENT_NAME);
        var document = FileSystemDocumentLoader.loadDocument(filePath);

        ingestor.ingest(document);

        var promptTemplate = PromptTemplate.from("""
            Answer the following question to the best of your ability: {{question}}
            Base your answer on the following information:
            {{information}}""");

        try (Scanner scanner = new Scanner(System.in)) {

            while(true) {

                System.out.println("Ask your question: ");
                String question = scanner.nextLine();

                if(question.equals("exit")) {
                    break;
                }

                // #2 - Retrieving the most relevant segments according to the question
                var relevantSegments = retriever.findRelevant(question);

                // #3 - Generating the prompt with the relevant segments
                var prompt = promptTemplate.apply(
                            Map.of(
                            "question", question,
                            "information", format(relevantSegments)));

                chatMemory.add(prompt.toUserMessage());

                var response = chatModel.generate(chatMemory.messages());

                chatMemory.add(response.content());

                System.out.println(response.content().text());
                System.out.println("\n\n########### TOKEN USAGE ############\n");
                System.out.println(response.tokenUsage());
            }
        }
    }

    private static String format(List<TextSegment> relevantSegments) {
        return relevantSegments.stream()
                .map(TextSegment::text)
                .map(segment -> "..." + segment + "...")
                .collect(joining("\n\n"));
    }

    private static Path toPath(String fileName) {
        try {
            URL fileUrl = _04_Retrieval.class.getResource(fileName);
            return Paths.get(fileUrl.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

}
