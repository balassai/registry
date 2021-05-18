/*
 * Copyright 2016-2021 Cloudera, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hortonworks.registries.schemaregistry.webservice.integration;

import com.hortonworks.registries.common.util.HadoopPlugin;
import com.hortonworks.registries.schemaregistry.CompatibilityResult;
import com.hortonworks.registries.schemaregistry.ISchemaRegistry;
import com.hortonworks.registries.schemaregistry.SchemaCompatibility;
import com.hortonworks.registries.schemaregistry.SchemaIdVersion;
import com.hortonworks.registries.schemaregistry.SchemaMetadata;
import com.hortonworks.registries.schemaregistry.SchemaMetadataInfo;
import com.hortonworks.registries.schemaregistry.SchemaValidationLevel;
import com.hortonworks.registries.schemaregistry.SchemaVersionInfo;
import com.hortonworks.registries.schemaregistry.SchemaVersionKey;
import com.hortonworks.registries.schemaregistry.authorizer.agent.AuthorizationAgent;
import com.hortonworks.registries.schemaregistry.authorizer.core.Authorizer;
import com.hortonworks.registries.schemaregistry.authorizer.core.util.AuthorizationUtils;
import com.hortonworks.registries.schemaregistry.webservice.ConfluentSchemaRegistryCompatibleResource;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.ClassRule;
import org.junit.Test;

import javax.ws.rs.BeanParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfluentSchemaRegistryCompatibleResourceIT {
        private static ISchemaRegistry schemaRegistryMock = mock(ISchemaRegistry.class);
        private static AuthorizationAgent authorizationAgentMock = mock(AuthorizationAgent.class);
        private static AuthorizationUtils authorizationUtils = new AuthorizationUtils(mock(HadoopPlugin.class));
        private Client testClient = RESOURCE.client();
        private static final Long TIMESTAMP = System.currentTimeMillis();

        @BeanParam
        public InputStream getInputStream() {
            return new ByteArrayInputStream("test".getBytes());
        }

        @ClassRule
        public static final ResourceTestRule RESOURCE = ResourceTestRule.builder()
                .addResource(instantiateResource())
                .addProperty("jersey.config.server.provider.classnames", MultiPartFeature.class.getName())
                .build();

        private static ConfluentSchemaRegistryCompatibleResource instantiateResource() {
            return new ConfluentSchemaRegistryCompatibleResource(schemaRegistryMock, authorizationAgentMock, authorizationUtils);
        }
        @Test
        public void getSubjectById() throws Exception {
                //given
                SchemaVersionInfo schemaVersionInfo = schemaVersionInfoCreator("name", 1L);
                when(schemaRegistryMock.getSchemaVersionInfo(new SchemaIdVersion(1L))).thenReturn(schemaVersionInfo);
                doNothing().when(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock), 
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));
                        
                //when
                Response response = testClient.target(
                        String.format("/api/v1/confluent/schemas/ids/1"))
                        .request()
                        .get();

                //then
                SchemaString expectedSchema = new SchemaString(schemaVersionInfo.getSchemaText());
                verify(schemaRegistryMock).getSchemaVersionInfo(new SchemaIdVersion(1L));
                verify(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock), 
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));
                String actual = response.readEntity(String.class);
                String expectedString = new StringBuilder().append("{\"schema\":\"").append(expectedSchema.getSchemaString()).append("\"}").toString();
                assertEquals(expectedString, actual);
                assertEquals(200, response.getStatus());
        }
        

        @Test
        public void findSubjects() throws Exception {
                //given
                SchemaMetadata schemaMetadata = schemaMetadataCreator("name");
                Collection<SchemaMetadataInfo> schemaMetadataInfos = schemaMetadataInfoCreator(schemaMetadata, 1L);
                when(schemaRegistryMock.findSchemaMetadata(any())).thenReturn(schemaMetadataInfos);
                when(authorizationAgentMock.authorizeFindSchemas(any(), any())).thenReturn(schemaMetadataInfos);

                //when
                Response response = testClient.target(
                        String.format("/api/v1/confluent/subjects"))
                        .request()
                        .get();

                //then
                verify(schemaRegistryMock).findSchemaMetadata(any());
                verify(authorizationAgentMock).authorizeFindSchemas(any(), any());
                String actual = response.readEntity(String.class);
                String expectedString = new StringBuilder().append("[\"").append(schemaMetadata.getName()).append("\"]").toString();
                assertEquals(expectedString, actual);
                assertEquals(200, response.getStatus());
        }

        @Test
        public void compatibilityLatestVersionTest() throws Exception {
                //given
                SchemaMetadata schemaMetadata = schemaMetadataCreator("complatest");
                SchemaMetadataInfo schemaMetadataInfo = new SchemaMetadataInfo(schemaMetadata, 2L, TIMESTAMP);
                SchemaVersionInfo schemaVersionInfo = schemaVersionInfoCreator("complatest", 2L);
                CompatibilityResult compatibilityResult = CompatibilityResult.SUCCESS;
                String schemaText = "{\"schema\":\"complatest_schema\"}";
                when(schemaRegistryMock.checkCompatibility("complatest", "complatest_schema")).thenReturn(compatibilityResult);
                when(schemaRegistryMock.getSchemaMetadataInfo("complatest")).thenReturn(schemaMetadataInfo);
                when(schemaRegistryMock.getLatestSchemaVersionInfo("complatest")).thenReturn(schemaVersionInfo);
                doNothing().when(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock),
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));

                //when
                Response response = testClient.target(
                        String.format("/api/v1/confluent/subjects/complatest/versions/latest"))
                        .request()
                        .post(Entity.json(schemaText), Response.class);

                //then
                verify(schemaRegistryMock).checkCompatibility("complatest", "complatest_schema");
                verify(schemaRegistryMock).getSchemaMetadataInfo("complatest");
                verify(schemaRegistryMock).getLatestSchemaVersionInfo("complatest");
                verify(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock),
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));
                CompatibilityResult actual = response.readEntity(CompatibilityResult.class);
                assertEquals(true, actual.isCompatible());
                assertEquals(200, response.getStatus());
        }

        @Test
        public void compatibilityVersionTest() throws Exception {
                //given
                SchemaMetadata schemaMetadata = schemaMetadataCreator("compvetest");
                SchemaMetadataInfo schemaMetadataInfo = new SchemaMetadataInfo(schemaMetadata, 2L, 3L);
                SchemaVersionInfo schemaVersionInfo = schemaVersionInfoCreator("compvetest", 2L);
                CompatibilityResult compatibilityResult = CompatibilityResult.SUCCESS;
                String schemaText = "{\"schema\":\"compevetest_schema\"}";
                when(schemaRegistryMock.checkCompatibility("compvetest", "compevetest_schema")).thenReturn(compatibilityResult);
                when(schemaRegistryMock.getSchemaMetadataInfo("compvetest")).thenReturn(schemaMetadataInfo);
                when(schemaRegistryMock.getSchemaVersionInfo(new SchemaVersionKey("compvetest", 4))).thenReturn(schemaVersionInfo);
                doNothing().when(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock),
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));

                //when
                Response response = testClient.target(
                        String.format("/api/v1/confluent/subjects/compvetest/versions/4"))
                        .request()
                        .post(Entity.json(schemaText), Response.class);

                //then
                verify(schemaRegistryMock).checkCompatibility("compvetest", "compevetest_schema");
                verify(schemaRegistryMock).getSchemaMetadataInfo("compvetest");
                verify(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock),
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));
                CompatibilityResult actual = response.readEntity(CompatibilityResult.class);
                assertEquals(true, actual.isCompatible());
                assertEquals(200, response.getStatus());
        }

        @Test
        public void compatibilityIncompatibleTest() throws Exception {
                //given
                SchemaMetadata schemaMetadata = schemaMetadataCreator("incompatible");
                SchemaMetadataInfo schemaMetadataInfo = new SchemaMetadataInfo(schemaMetadata, 2L, 3L);
                SchemaVersionInfo schemaVersionInfo = schemaVersionInfoCreator("incompatible", 2L);
                CompatibilityResult compatibilityResult = CompatibilityResult.createIncompatibleResult("not compatible", "somewhere", "incompatible");
                String schemaText = "{\"schema\":\"incompatible_schema\"}";
                when(schemaRegistryMock.checkCompatibility("incompatible", "incompatible_schema")).thenReturn(compatibilityResult);
                when(schemaRegistryMock.getSchemaMetadataInfo("incompatible")).thenReturn(schemaMetadataInfo);
                when(schemaRegistryMock.getSchemaVersionInfo(new SchemaVersionKey("incompatible", 4))).thenReturn(schemaVersionInfo);
                doNothing().when(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock),
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));

                //when
                Response response = testClient.target(
                        String.format("/api/v1/confluent/subjects/incompatible/versions/4"))
                        .request()
                        .post(Entity.json(schemaText), Response.class);

                //then
                verify(schemaRegistryMock).checkCompatibility("incompatible", "incompatible_schema");
                verify(schemaRegistryMock).getSchemaMetadataInfo("incompatible");
                verify(schemaRegistryMock).getSchemaVersionInfo(new SchemaVersionKey("incompatible", 4));
                verify(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock),
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));
                CompatibilityResult actual = response.readEntity(CompatibilityResult.class);
                assertEquals(false, actual.isCompatible());
                assertEquals(200, response.getStatus());
        }

        @Test
        public void compatibilityIncompatibleLatestTest() throws Exception {
                //given
                SchemaMetadata schemaMetadata = schemaMetadataCreator("incompatible-latest");
                SchemaMetadataInfo schemaMetadataInfo = new SchemaMetadataInfo(schemaMetadata, 2L, 3L);
                SchemaVersionInfo schemaVersionInfo = schemaVersionInfoCreator("incompatible-latest", 2L);
                CompatibilityResult compatibilityResult = CompatibilityResult.createIncompatibleResult("not compatible", "somewhere", "incompatible-latest");
                String schemaText = "{\"schema\":\"incompatible-latest_schema\"}";
                when(schemaRegistryMock.checkCompatibility("incompatible-latest", "incompatible-latest_schema")).thenReturn(compatibilityResult);
                when(schemaRegistryMock.getSchemaMetadataInfo("incompatible-latest")).thenReturn(schemaMetadataInfo);
                when(schemaRegistryMock.getSchemaVersionInfo(new SchemaVersionKey("incompatible-latest", 4))).thenReturn(schemaVersionInfo);
                doNothing().when(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock),
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));

                //when
                Response response = testClient.target(
                        String.format("/api/v1/confluent/subjects/incompatible-latest/versions/4"))
                        .request()
                        .post(Entity.json(schemaText), Response.class);

                //then
                verify(schemaRegistryMock).checkCompatibility("incompatible-latest", "incompatible-latest_schema");
                verify(schemaRegistryMock).getSchemaMetadataInfo("incompatible-latest");
                verify(authorizationAgentMock).authorizeSchemaVersion(any(), eq(schemaRegistryMock),
                        eq(schemaVersionInfo), eq(Authorizer.AccessType.READ));
                CompatibilityResult actual = response.readEntity(CompatibilityResult.class);
                assertEquals(false, actual.isCompatible());
                assertEquals(200, response.getStatus());
        }
        
        private SchemaMetadata schemaMetadataCreator(String name) {
                return new SchemaMetadata.Builder(name)
                        .type("type")
                        .schemaGroup("group")
                        .description("desc")
                        .compatibility(SchemaCompatibility.FORWARD)
                        .validationLevel(SchemaValidationLevel.LATEST)
                        .evolve(false)
                        .build();
        }
        
        private Collection<SchemaMetadataInfo> schemaMetadataInfoCreator(SchemaMetadata metadata, Long id) {
                SchemaMetadataInfo schemaMetadataInfo = new SchemaMetadataInfo(metadata, id, TIMESTAMP);
                Collection<SchemaMetadataInfo> schemaMetadataInfos = new ArrayList<>();
                schemaMetadataInfos.add(schemaMetadataInfo);
                return schemaMetadataInfos;
        }
        
        private SchemaVersionInfo schemaVersionInfoCreator(String name, Long id) {
                return new SchemaVersionInfo(id, name, 1, "text", TIMESTAMP, "desc");
        }
        
}
