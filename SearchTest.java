import io.github.jukomu.jmcomic.api.model.*;
import io.github.jukomu.jmcomic.core.JmComic;
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient;
import io.github.jukomu.jmcomic.core.config.JmConfiguration;
import io.github.jukomu.jmcomic.api.enums.ClientType;

public class SearchTest {
    public static void main(String[] args) throws Exception {
        JmApiClient client = JmComic.newApiClient(
            new JmConfiguration.Builder()
                .clientType(ClientType.API)
                .retryTimes(3)
                .build()
        );

        SearchQuery query = new SearchQuery.Builder()
            .text("\u304f\u304c\u3089")
            .page(1)
            .build();

        System.out.println("Searching: " + query.getSearchQuery());
        JmSearchPage page = client.search(query);
        System.out.println("Results: page=" + page.getCurrentPage() +
            " totalPages=" + page.getTotalPages() +
            " totalItems=" + page.getTotalItems());

        for (JmAlbumMeta album : page.getContent()) {
            System.out.println("  - [" + album.getId() + "] " + album.getTitle());
        }
        System.out.println("Done");
    }
}