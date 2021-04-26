package com.dlb.dlb;

import com.dlb.dlb.configration.DLBConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

@SpringBootTest
class ServerApplicationTests {
	@Autowired
	private DLBConfiguration.URIMapping uriMapping;

	@Autowired
	private DLBConfiguration.UpstreamServerGroups serverGroups;

	@Test
	void contextLoads() {
		Map<String, DLBConfiguration.UpstreamServerGroup> map = serverGroups.getMap();

		System.out.println("hello");
	}

}
