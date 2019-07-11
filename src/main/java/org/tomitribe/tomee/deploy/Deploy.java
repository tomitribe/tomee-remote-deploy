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

import javax.annotation.security.RolesAllowed;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.naming.InitialContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;

@Singleton
@Lock(LockType.READ)
//@RolesAllowed("deploy-api")
@Path("deploy")
public class Deploy {

    private final Logger logger = Logger.getInstance(LogCategory.OPENEJB_DEPLOY, Deploy.class);

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@Multipart("artifact") Attachment attachment) {
        try {
            final String filename = attachment.getContentDisposition().getParameter("filename");
            final InputStream is = attachment.getObject(InputStream.class);

            // copy the artifact to a temporary file
            final File tempFile = File.createTempFile("deploy-", ".tmp");
            tempFile.deleteOnExit();
            IO.copy(is, tempFile);
            is.close();

            // lookup the deployer EJB
            final InitialContext initialContext = new InitialContext();
            final Deployer deployer = (Deployer) initialContext.lookup("openejb:remote/openejb/DeployerBusinessRemote");

            // perform the deployment
            final Properties deployProperties = new Properties();
            deployProperties.setProperty("filename", filename);
            final AppInfo appInfo = deployer.deploy(tempFile.getAbsolutePath(), deployProperties);

            // return
            return Response.ok().build();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Response.status(500).entity(e.getMessage()).build();
        }
    }
}
