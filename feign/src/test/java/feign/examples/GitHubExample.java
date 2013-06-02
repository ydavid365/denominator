package feign.examples;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.Feign.Config;
import feign.Request;
import feign.codec.Decoder;
import feign.codec.RegexDecoder.Regex;

/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

    /**
     * uses regular expressions to parse the response into a map.
     */
    interface GitHubNoDeps {
        @GET
        @Path("/repos/{owner}/{repo}/contributors")
        // regex may look awkward, but it is fast and allows you to reuse types like Map or Table.
        @Regex(pattern = "\"login\":\"([^\"]+)\".*?\"contributions\":([0-9]+)")
        Map<String, String> contributors(@PathParam("owner") String owner, @PathParam("repo") String repo);
    }

    public static void runGitHub() {

        GitHubNoDeps github = Feign.create(GitHubNoDeps.class, "https://api.github.com");

        // Fetch and print a list of the contributors to this library.
        Map<String, String> contributors = github.contributors("netflix", "denominator");
        for (Entry<String, String> contributor : contributors.entrySet()) {
            System.out.println(contributor.getKey() + " (" + contributor.getValue() + ")");
        }
    }

    // As many folks aren't familiar with regex, and probably prefer gson or
    // jackson, here's how to plugin your favorite library to feign.

    static class Contributor {
        String login;
        int contributions;
    }

    interface TypedGitHub {
        @GET
        @Path("/repos/{owner}/{repo}/contributors")
        List<Contributor> contributors(@PathParam("owner") String owner, @PathParam("repo") String repo);
    }

    public static void runGitHubWithGson() {

        TypedGitHub github = Feign.create(TypedGitHub.class, "https://api.github.com", new GsonSupport());

        // Fetch and print a list of the contributors to this library.
        List<Contributor> contributors = github.contributors("netflix", "denominator");
        for (Contributor contributor : contributors) {
            System.out.println(contributor.login + " (" + contributor.contributions + ")");
        }
    }

    @Module(overrides = true, library = true)
    static class GsonSupport {

        @Provides(type = Provides.Type.SET)
        Config<Decoder> gitHubUsesGson() {
            return Config.<Decoder> create(TypedGitHub.class, new Decoder() {
                Gson gson = new Gson();
                @Override
                protected Object decode(Request request, Reader reader, TypeToken<?> type) {
                    return gson.fromJson(reader, type.getType());
                }
            });
        }
    }

    public static void main(String... args) {
        runGitHub();
    }
}
