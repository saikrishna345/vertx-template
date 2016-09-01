/*
 * Copyright 2016 The Board of Trustees of The Leland Stanford Junior University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.susom.app.server.container;

import com.github.susom.database.Config;
import com.github.susom.vertx.base.BasePolicy;
import com.github.susom.vertx.base.PortInfo;
import java.io.File;
import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.Permission;
import java.security.Permissions;
import java.util.ArrayList;
import java.util.List;

/**
 * Configure and install a policy for the Java SecurityManager to use when
 * running the server inside the sandbox.
 */
public class SecurityPolicy extends BasePolicy {
  // This class intentionally does not do anything that might initialize logging,
  // and is careful about even console logging (based on -Djava.security.debug)
  // because it is very easy to cause infinite recursion if you do anything here
  // that hits a security manager permission.
  private final List<Permission> perms = new ArrayList<>();

  public SecurityPolicy() throws Exception {
    String propertiesFile = System.getProperty("properties","conf/app.properties:local.properties:sample.properties");
    Config config = Config.from().systemProperties().propertyFile(propertiesFile.split(File.pathSeparator)).get();

    // Our server must listen on a local port
    PortInfo listen = PortInfo.parseUrl(config.getString("listen.url", "http://localhost:8000"));
    perms.add(new SocketPermission(listen.host() + ":" + listen.port(), "listen,resolve"));

    // For fake security we need to act as a client to our own embedded authentication
    if (config.getBooleanOrFalse("insecure.fake.security")) {
      perms.add(new SocketPermission("localhost:" + listen.port(), "connect,resolve"));
    }

    // Connecting to centralized authentication server

    String authServerUri = config.getString("auth.server.base.uri");
    if (authServerUri != null) {
      PortInfo authServer = PortInfo.parseUrl(authServerUri);
      perms.add(new SocketPermission(authServer.host() + ":" + authServer.port(), "connect,resolve"));
    }

    // If we need to connect to something like an email server
//    host = config.getString("email.server.host");
//    port = config.getString("email.server.port");
//    if (host != null && port != null) {
//      perms.add(new SocketPermission(host + ":" + port, "connect,resolve"));
//    }

    // These two are for hsqldb to store its database files
    if (config.getString("database.url", "").startsWith("jdbc:hsqldb:file:.hsql/")) {
      perms.add(new FilePermission(workDir + "/.hsql", "read,write,delete"));
      perms.add(new FilePermission(workDir + "/.hsql/-", "read,write,delete"));
    }

    // TODO read these before sandboxing and deny permission later?
    perms.add(new FilePermission(workDir + "/local.jwt.jceks", "read"));
    perms.add(new FilePermission(workDir + "/local.ssl.jks", "read"));
  }

  @Override
  protected void addAppPermissions(Permissions appPerms) {
    for (Permission permission : perms) {
      appPerms.add(permission);
    }
  }
}
