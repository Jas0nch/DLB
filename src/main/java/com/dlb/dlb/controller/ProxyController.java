package com.dlb.dlb.controller;


import com.dlb.dlb.configration.DLBConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Set;

@RestController
public class ProxyController {
    @Autowired
    private DLBConfiguration.UpstreamServerGroups serverGroups;

    @Autowired
    private DLBConfiguration.URIMapping uriMapping;

    @GetMapping("/**")
    @ResponseBody
    public Mono<String> proxy(ServerHttpRequest request) {
        String path = request.getPath().toString();

        Set<String> allUris = uriMapping.allUris();

        String matchedUri = "";

        for (String uri : allUris) {
            if (path.startsWith(uri) && uri.length() > matchedUri.length()) {
                matchedUri = uri;
            }
        }

        if (matchedUri.isEmpty()) return null;

        String groupName = uriMapping.groupName(matchedUri);
        String param = path.substring(matchedUri.length());

        DLBConfiguration.UpstreamServerGroup serverGroup = serverGroups.serverGroup(groupName);

        String server = serverGroup.taskServer();

        // send request
        WebClient client = WebClient.builder()
                            .baseUrl(server)
                            .build();

        Mono<String> mono = client.get()
                .uri(param)
                .retrieve()
                .bodyToMono(String.class);

        return mono;
    }

    @GetMapping("/hyu/test")
    public String mytest() {
        DLBConfiguration.UpstreamServerGroup serverGroup = serverGroups.serverGroup("book_search");
        DLBConfiguration.UpstreamServer toAdd = serverGroup.getServers().get(0);
        serverGroup.getRunningServers().add(toAdd);

        return "ok";
    }
}
