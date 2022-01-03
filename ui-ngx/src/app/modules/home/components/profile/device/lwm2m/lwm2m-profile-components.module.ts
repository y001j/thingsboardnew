///
/// Copyright © 2016-2021 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { NgModule } from '@angular/core';
import { Lwm2mDeviceProfileTransportConfigurationComponent } from './lwm2m-device-profile-transport-configuration.component';
import { Lwm2mObjectListComponent } from './lwm2m-object-list.component';
import { Lwm2mObserveAttrTelemetryComponent } from './lwm2m-observe-attr-telemetry.component';
import { Lwm2mObserveAttrTelemetryResourcesComponent } from './lwm2m-observe-attr-telemetry-resources.component';
import { Lwm2mAttributesDialogComponent } from './lwm2m-attributes-dialog.component';
import { Lwm2mAttributesComponent } from './lwm2m-attributes.component';
import { Lwm2mAttributesKeyListComponent } from './lwm2m-attributes-key-list.component';
import { Lwm2mDeviceConfigServerComponent } from './lwm2m-device-config-server.component';
import { Lwm2mObjectAddInstancesDialogComponent } from './lwm2m-object-add-instances-dialog.component';
import { Lwm2mObjectAddInstancesListComponent } from './lwm2m-object-add-instances-list.component';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@app/shared/shared.module';
import { Lwm2mObserveAttrTelemetryInstancesComponent } from './lwm2m-observe-attr-telemetry-instances.component';
import { DeviceProfileCommonModule } from '@home/components/profile/device/common/device-profile-common.module';

@NgModule({
  declarations:
    [
      Lwm2mDeviceProfileTransportConfigurationComponent,
      Lwm2mObjectListComponent,
      Lwm2mObserveAttrTelemetryComponent,
      Lwm2mObserveAttrTelemetryResourcesComponent,
      Lwm2mAttributesDialogComponent,
      Lwm2mAttributesComponent,
      Lwm2mAttributesKeyListComponent,
      Lwm2mDeviceConfigServerComponent,
      Lwm2mObjectAddInstancesDialogComponent,
      Lwm2mObjectAddInstancesListComponent,
      Lwm2mObserveAttrTelemetryInstancesComponent
    ],
  imports: [
    CommonModule,
    SharedModule,
    DeviceProfileCommonModule
   ],
  exports: [
    Lwm2mDeviceProfileTransportConfigurationComponent,
    Lwm2mObjectListComponent,
    Lwm2mObserveAttrTelemetryComponent,
    Lwm2mObserveAttrTelemetryResourcesComponent,
    Lwm2mAttributesDialogComponent,
    Lwm2mAttributesComponent,
    Lwm2mAttributesKeyListComponent,
    Lwm2mDeviceConfigServerComponent,
    Lwm2mObjectAddInstancesDialogComponent,
    Lwm2mObjectAddInstancesListComponent,
    Lwm2mObserveAttrTelemetryInstancesComponent
  ],
  providers: [
  ]
})
export class Lwm2mProfileComponentsModule { }
