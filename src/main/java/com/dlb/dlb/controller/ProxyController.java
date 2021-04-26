package com.dlb.dlb.controller;


import com.dlb.dlb.configration.DLBConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
public class ProxyController {
    @Autowired
    private DLBConfiguration.UpstreamServerGroups serverGroups;

    @Autowired
    private DLBConfiguration.URIMapping uriMapping;

    @GetMapping("/**")
    @ResponseBody
    public Object proxy(ServerHttpRequest request) {
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

        return server;
    }
}
