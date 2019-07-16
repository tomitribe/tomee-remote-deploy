/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.tomitribe.tomee.deploy;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.openejb.assembler.Deployer;
import org.apache.openejb.assembler.classic.AppInfo;
import org.apache.openejb.loader.IO;
import org.apache.openejb.util.LogCategory;
import org.apache.openejb.util.Logger;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.SessionContext;
import javax.ejb.Singleton;
import javax.naming.InitialContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Singleton
@Lock(LockType.READ)
@RolesAllowed({ "manager-gui", "manager-script" })
@Path("")
public class Deploy {

    @Resource
    private SessionContext ctx;

    private final Logger logger = Logger.getInstance(LogCategory.OPENEJB_DEPLOY, Deploy.class);

    @POST
    @Path("deploy")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@Multipart("artifact") Attachment attachment) {
        try {
            final String filename = attachment.getContentDisposition().getParameter("filename");
            final InputStream is = attachment.getObject(InputStream.class);

            // copy the artifact to a temporary file
            final File tempFile = File.createTempFile("deploy-", "-tmp");
            tempFile.delete();
            tempFile.mkdirs();
            tempFile.deleteOnExit();
            final File target = new File(tempFile, filename);
            target.deleteOnExit();
            IO.copy(is, target);
            is.close();

            // lookup the deployer EJB
            final InitialContext initialContext = new InitialContext();
            final Deployer deployer = (Deployer) initialContext.lookup("openejb:remote/openejb/DeployerBusinessRemote");

            // perform the deployment
            final Properties deployProperties = new Properties();
            deployProperties.setProperty("filename", filename);
            final AppInfo appInfo = deployer.deploy(target.getAbsolutePath(), deployProperties);

            // return
            return Response.ok(appInfo.appId).build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("undeploy/{moduleId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response undeployArtifact(@PathParam("moduleId") final String moduleId) {
        try {
            // lookup the deployer EJB
            final InitialContext initialContext = new InitialContext();
            final Deployer deployer = (Deployer) initialContext.lookup("openejb:remote/openejb/DeployerBusinessRemote");

            final Collection<AppInfo> deployedApps = deployer.getDeployedApps();
            String path = null;
            for (final AppInfo deployedApp : deployedApps) {
                if (deployedApp.appId.equals(moduleId)) {
                    path = deployedApp.path;
                    break;
                }
            }

            if (path == null) {
                return Response.status(404).build();
            }

            deployer.undeploy(path);
            return Response.ok().build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listDeployedApplications() {
        try {
            // lookup the deployer EJB
            final InitialContext initialContext = new InitialContext();
            final Deployer deployer = (Deployer) initialContext.lookup("openejb:remote/openejb/DeployerBusinessRemote");

            final List<String> deployedApps = deployer.getDeployedApps().stream().map(a -> a.appId).collect(Collectors.toList());

            return Response.ok(deployedApps).build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Response.status(500).entity(e.getMessage()).build();
        }
    }
}
