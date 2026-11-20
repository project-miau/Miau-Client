package idle.miau.utility.spotify.http;

import java.io.IOException;

public interface HttpTransport {
    HttpResponse execute(HttpRequest request) throws IOException;
}
