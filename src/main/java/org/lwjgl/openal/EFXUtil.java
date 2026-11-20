/*
 * Copyright LWJGL. All rights reserved. License terms: http://lwjgl.org/license.php
 */
package org.lwjgl.openal;


import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.EXTEfx.*;









public final class EFXUtil {

    


    private static final int EFFECT = 1111;
    


    private static final int FILTER = 2222;

    


    private EFXUtil() {
    }

    





    public static boolean isEfxSupported() {
        return ALC.getCapabilities().ALC_EXT_EFX;
    }

    









    public static boolean isEffectSupported(int effectType) {
        
        switch (effectType) {
            case AL_EFFECT_NULL:
            case AL_EFFECT_EAXREVERB:
            case AL_EFFECT_REVERB:
            case AL_EFFECT_CHORUS:
            case AL_EFFECT_DISTORTION:
            case AL_EFFECT_ECHO:
            case AL_EFFECT_FLANGER:
            case AL_EFFECT_FREQUENCY_SHIFTER:
            case AL_EFFECT_VOCAL_MORPHER:
            case AL_EFFECT_PITCH_SHIFTER:
            case AL_EFFECT_RING_MODULATOR:
            case AL_EFFECT_AUTOWAH:
            case AL_EFFECT_COMPRESSOR:
            case AL_EFFECT_EQUALIZER:
                break;
            default:
                throw new IllegalArgumentException("Unknown or invalid effect type: " + effectType);
        }

        return testSupportGeneric(EFFECT, effectType);
    }

    









    public static boolean isFilterSupported(int filterType) {
        
        switch (filterType) {
            case AL_FILTER_NULL:
            case AL_FILTER_LOWPASS:
            case AL_FILTER_HIGHPASS:
            case AL_FILTER_BANDPASS:
                break;
            default:
                throw new IllegalArgumentException("Unknown or invalid filter type: " + filterType);
        }

        return testSupportGeneric(FILTER, filterType);
    }

    







    private static boolean testSupportGeneric(int objectType, int typeValue) {
        
        switch (objectType) {
            case EFFECT:
            case FILTER:
                break;
            default:
                throw new IllegalArgumentException("Invalid objectType: " + objectType);
        }

        boolean supported = false;
        if (isEfxSupported()) {

            
            alGetError();
            int genError;
            int testObject = 0;
            try {
                switch (objectType) { 
                    case EFFECT:
                        testObject = alGenEffects();
                        break;
                    case FILTER:
                        testObject = alGenFilters();
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid objectType: " + objectType);
                }
                ;
                genError = alGetError();
            } catch (OpenALException debugBuildException) {
                
                
                if (debugBuildException.getMessage().contains("AL_OUT_OF_MEMORY")) {
                    genError = AL_OUT_OF_MEMORY;
                } else {
                    genError = AL_INVALID_OPERATION;
                }
            }

            if (genError == AL_NO_ERROR) {
                
                alGetError();
                int setError;
                try {
                    switch (objectType) { 
                        case EFFECT:
                            alEffecti(testObject, AL_EFFECT_TYPE, typeValue);
                            break;
                        case FILTER:
                            alFilteri(testObject, AL_FILTER_TYPE, typeValue);
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid objectType: " + objectType);
                    }
                    setError = alGetError();
                } catch (OpenALException debugBuildException) {
                    
                    
                    setError = AL_INVALID_VALUE;
                }

                if (setError == AL_NO_ERROR) {
                    supported = true;
                }

                
                try {
                    switch (objectType) { 
                        case EFFECT:
                            alDeleteEffects(testObject);
                            break;
                        case FILTER:
                            alDeleteFilters(testObject);
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid objectType: " + objectType);
                    }
                } catch (OpenALException debugBuildException) {
                    
                }

            } else if (genError == AL_OUT_OF_MEMORY) {
                throw new OpenALException(AL10.alGetString(genError));
            }
        }

        return supported;
    }
}
