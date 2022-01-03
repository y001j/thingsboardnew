/**
 * Copyright © 2016-2021 The Thingsboard Authors
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
package org.thingsboard.server.transport.lwm2m.server.client;

import org.eclipse.leshan.core.model.ResourceModel;
import org.eclipse.leshan.core.node.LwM2mMultipleResource;
import org.eclipse.leshan.core.node.LwM2mResourceInstance;

import java.io.Serializable;
import java.util.Collection;

public class TbLwM2MMultipleResource extends LwM2mMultipleResource implements TbLwM2MResource, Serializable {

    private static final long serialVersionUID = 4658477128628087186L;

    public TbLwM2MMultipleResource(int id, ResourceModel.Type type, TbLwM2MResourceInstance... instances) {
        super(id, type, instances);
    }
}
