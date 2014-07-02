/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.hardware.camera2.legacy;

import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.util.Log;
import android.util.Size;

import static com.android.internal.util.Preconditions.*;
import static android.hardware.camera2.CaptureResult.*;

/**
 * Provide legacy-specific implementations of camera2 CaptureResult for legacy devices.
 */
public class LegacyResultMapper {
    private static final String TAG = "LegacyResultMapper";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * Generate capture result metadata from the legacy camera request.
     *
     * @param legacyRequest a non-{@code null} legacy request containing the latest parameters
     * @param timestamp the timestamp to use for this result in nanoseconds.
     *
     * @return a {@link CameraMetadataNative} object containing result metadata.
     */
    public static CameraMetadataNative convertResultMetadata(LegacyRequest legacyRequest,
                                                      long timestamp) {
        CameraCharacteristics characteristics = legacyRequest.characteristics;
        CaptureRequest request = legacyRequest.captureRequest;
        Size previewSize = legacyRequest.previewSize;
        Camera.Parameters params = legacyRequest.parameters;

        CameraMetadataNative result = new CameraMetadataNative();

        /*
         * control
         */
        // control.afState
        if (LegacyMetadataMapper.LIE_ABOUT_AF) {
            // TODO: Implement autofocus state machine
            result.set(CaptureResult.CONTROL_AF_MODE, request.get(CaptureRequest.CONTROL_AF_MODE));
        }

        /*
         * control.ae*
         */
        mapAe(result, /*out*/params);

        // control.awbLock
        result.set(CaptureResult.CONTROL_AWB_LOCK, params.getAutoWhiteBalanceLock());

        // control.awbState
        if (LegacyMetadataMapper.LIE_ABOUT_AWB) {
            // Lie to pass CTS temporarily.
            // TODO: CTS needs to be updated not to query this value
            // for LIMITED devices unless its guaranteed to be available.
            result.set(CaptureResult.CONTROL_AWB_STATE,
                    CameraMetadata.CONTROL_AWB_STATE_CONVERGED);
            // TODO: Read the awb mode from parameters instead
            result.set(CaptureResult.CONTROL_AWB_MODE,
                    request.get(CaptureRequest.CONTROL_AWB_MODE));
        }

        /*
         * lens
         */
        // lens.focalLength
        result.set(CaptureResult.LENS_FOCAL_LENGTH, params.getFocalLength());

        /*
         * scaler
         */
        mapScaler(result, characteristics, request, previewSize, params);

        /*
         * sensor
         */
        // sensor.timestamp
        result.set(CaptureResult.SENSOR_TIMESTAMP, timestamp);

        // TODO: Remaining result metadata tags conversions.
        return result;
    }

    private static void mapAe(CameraMetadataNative m, /*out*/Parameters p) {
        // control.aeAntiBandingMode
        {
            int antiBandingMode = LegacyMetadataMapper.convertAntiBandingModeOrDefault(
                    p.getAntibanding());
            m.set(CONTROL_AE_ANTIBANDING_MODE, antiBandingMode);
        }

        // control.aeMode, flash.mode
        mapAeAndFlashMode(m, p);

        // control.aeState
        if (LegacyMetadataMapper.LIE_ABOUT_AE_STATE) {
            // Lie to pass CTS temporarily.
            // TODO: Implement precapture trigger, after which we can report CONVERGED ourselves
            m.set(CONTROL_AE_STATE, CONTROL_AE_STATE_CONVERGED);
        }
    }


    /** Map results for control.aeMode, flash.mode */
    private static void mapAeAndFlashMode(CameraMetadataNative m, /*out*/Parameters p) {
        // Default: AE mode on but flash never fires
        int flashMode = FLASH_MODE_OFF;
        int aeMode = CONTROL_AE_MODE_ON;

        switch (p.getFlashMode()) {
            case Parameters.FLASH_MODE_OFF:
                break; // ok, using default
            case Parameters.FLASH_MODE_AUTO:
                aeMode = CONTROL_AE_MODE_ON_AUTO_FLASH;
                break;
            case Parameters.FLASH_MODE_ON:
                // flashMode = SINGLE + aeMode = ON is indistinguishable from ON_ALWAYS_FLASH
                aeMode = CONTROL_AE_MODE_ON_ALWAYS_FLASH;
                break;
            case Parameters.FLASH_MODE_RED_EYE:
                aeMode = CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE;
                break;
            case Parameters.FLASH_MODE_TORCH:
                flashMode = FLASH_MODE_TORCH;
                break;
            default:
                Log.w(TAG, "mapAeAndFlashMode - Ignoring unknown flash mode " + p.getFlashMode());
        }

        // flash.mode
        m.set(FLASH_MODE, flashMode);
        // control.aeMode
        m.set(CONTROL_AE_MODE, aeMode);
    }

    /** Map results for scaler.* */
    private static void mapScaler(CameraMetadataNative m,
            CameraCharacteristics characteristics,
            CaptureRequest request,
            Size previewSize,
            /*out*/Parameters p) {
        /*
         * scaler.cropRegion
         */
        {
            Rect activeArraySize = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Rect activeArraySizeOnly = new Rect(
                    /*left*/0, /*top*/0,
                    activeArraySize.width(), activeArraySize.height());

            Rect userCropRegion = request.get(CaptureRequest.SCALER_CROP_REGION);

            if (userCropRegion == null) {
                userCropRegion = activeArraySizeOnly;
            }

            Rect reportedCropRegion = new Rect();
            Rect previewCropRegion = new Rect();
            ParameterUtils.getClosestAvailableZoomCrop(p, activeArraySizeOnly,
                    previewSize, userCropRegion,
                    /*out*/reportedCropRegion, /*out*/previewCropRegion);

            m.set(SCALER_CROP_REGION, reportedCropRegion);
        }
    }
}
