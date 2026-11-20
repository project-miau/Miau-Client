
package paulscode.sound.libraries;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import javax.sound.sampled.AudioFormat;


import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;

import org.lwjgl.system.MemoryUtil;
import paulscode.sound.Channel;
import paulscode.sound.FilenameURL;
import paulscode.sound.ICodec;
import paulscode.sound.Library;
import paulscode.sound.ListenerData;
import paulscode.sound.SoundBuffer;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
import paulscode.sound.Source;

/**
 * The LibraryLWJGLOpenAL class interfaces the lwjgl binding of OpenAL.
 *<b><br><br>
 *    This software is based on or using the LWJGL Lightweight Java Gaming
 *    Library available from
 *    http://www.lwjgl.org/.
 *</b><br><br>
 *    LWJGL License:
 *<br><i>
 * Copyright (c) 2002-2008 Lightweight Java Game Library Project
 * All rights reserved.
 *<br>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * <br>
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *<br>
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *<br>
 * * Neither the name of 'Light Weight Java Game Library' nor the names of
 *   its contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 * <br>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * <br><br><br></i>
 *<b><i>    SoundSystem LibraryLWJGLOpenAL License:</b></i><br><b><br>
 *<b>
 *    You are free to use this library for any purpose, commercial or otherwise.
 *    You may modify this library or source code, and distribute it any way you
 *    like, provided the following conditions are met:
 *<br>
 *    1) You must abide by the conditions of the aforementioned LWJGL License.
 *<br>
 *    2) You may not falsely claim to be the author of this library or any
 *    unmodified portion of it.
 *<br>
 *    3) You may not copyright this library or a modified version of it and then
 *    sue me for copyright infringement.
 *<br>
 *    4) If you modify the source code, you must clearly document the changes
 *    made before redistributing the modified source code, so other users know
 *    it is not the original code.
 *<br>
 *    5) You are not required to give me credit for this library in any derived
 *    work, but if you do, you must also mention my website:
 *    http://www.paulscode.com
 *<br>
 *    6) I the author will not be responsible for any damages (physical,
 *    financial, or otherwise) caused by the use if this library or any part
 *    of it.
 *<br>
 *    7) I the author do not guarantee, warrant, or make any representations,
 *    either expressed or implied, regarding the use of this library or any
 *    part of it.
 * <br><br>
 *    Author: Paul Lamb
 * <br>
 *    http://www.paulscode.com
 * </b>
 */
public class LibraryLWJGLOpenAL extends Library
{
    private long openAlDevice = MemoryUtil.NULL;
    private long openAlContext = MemoryUtil.NULL;
    



    private static final boolean GET = false;
    


    private static final boolean SET = true;
    



    private static final boolean XXX = false;

    


    private FloatBuffer listenerPositionAL = null;
    


    private FloatBuffer listenerOrientation = null;
    


    private FloatBuffer listenerVelocity = null;
    


    private HashMap<String, IntBuffer> ALBufferMap = null;

    


    private static boolean alPitchSupported = true;

    




    public LibraryLWJGLOpenAL() throws SoundSystemException
    {
        super();
        ALBufferMap = new HashMap<String, IntBuffer>();
        reverseByteOrder = true;
    }

    


    @Override
    public void init() throws SoundSystemException
    {
        boolean errors = false; 

        try
        {
            
            openAlDevice = ALC10.alcOpenDevice((ByteBuffer) null);
            if (openAlDevice == MemoryUtil.NULL) {
                throw new IllegalStateException("Failed to open the default OpenAL device.");
            }
            ALCCapabilities deviceCaps = ALC.createCapabilities(openAlDevice);
            openAlContext = ALC10.alcCreateContext(openAlDevice, (IntBuffer) null);
            if (openAlContext == MemoryUtil.NULL) {
                throw new IllegalStateException("Failed to create OpenAL context.");
            }
            ALC10.alcMakeContextCurrent(openAlContext);
            AL.createCapabilities(deviceCaps);
            
            errors = checkALError();
        }
        catch( java.lang.Exception e )
        {
            
            errorMessage( "Unable to initialize OpenAL.  Probable cause: " +
                    "OpenAL not supported." );
            printStackTrace( e );
            throw new Exception( e.getMessage(),
                    Exception.CREATE );
        }

        
        if( errors )
            importantMessage( "OpenAL did not initialize properly!" );
        else
            message( "OpenAL initialized." );

        
        listenerPositionAL = BufferUtils.createFloatBuffer( 3 ).put(
                new float[] { listener.position.x,
                        listener.position.y,
                        listener.position.z } );
        listenerOrientation = BufferUtils.createFloatBuffer( 6 ).put (
                new float[] { listener.lookAt.x, listener.lookAt.y,
                        listener.lookAt.z, listener.up.x, listener.up.y,
                        listener.up.z } );
        listenerVelocity = BufferUtils.createFloatBuffer( 3 ).put (
                new float[] { 0.0f, 0.0f, 0.0f } );

        
        listenerPositionAL.flip();
        listenerOrientation.flip();
        listenerVelocity.flip();

        
        AL10.alListenerfv( AL10.AL_POSITION, listenerPositionAL );
        errors = checkALError() || errors;
        AL10.alListenerfv( AL10.AL_ORIENTATION, listenerOrientation );
        errors = checkALError() || errors;
        AL10.alListenerfv( AL10.AL_VELOCITY, listenerVelocity );
        errors = checkALError() || errors;

        AL10.alDopplerFactor( SoundSystemConfig.getDopplerFactor() );
        errors = checkALError() || errors;

        AL10.alDopplerVelocity( SoundSystemConfig.getDopplerVelocity() );
        errors = checkALError() || errors;

        
        if( errors )
        {
            importantMessage( "OpenAL did not initialize properly!" );
            throw new Exception( "Problem encountered " +
                    "while loading OpenAL or " +
                    "creating the listener.  " +
                    "Probable cause:  OpenAL not " +
                    "supported",
                    Exception.CREATE );
        }

        super.init();

        
        ChannelLWJGLOpenAL channel = (ChannelLWJGLOpenAL)
                normalChannels.get( 1 );
        try
        {
            AL10.alSourcef( channel.ALSource.get( 0 ),
                    AL10.AL_PITCH, 1.0f );
            if( checkALError() )
            {
                alPitchSupported( SET, false );
                throw new Exception( "OpenAL: AL_PITCH not " +
                        "supported.", Exception.NO_AL_PITCH );
            }
            else
            {
                alPitchSupported( SET, true );
            }
        }
        catch( java.lang.Exception e )
        {
            alPitchSupported( SET, false );
            throw new Exception( "OpenAL: AL_PITCH not " +
                    "supported.", Exception.NO_AL_PITCH );
        }
    }

    



    public static boolean libraryCompatible()
    {
        if( AL.getCapabilities() != null)
            return true;

        long device = MemoryUtil.NULL;
        long context = MemoryUtil.NULL;
        try
        {
            device = ALC10.alcOpenDevice((ByteBuffer) null);
            if (device == MemoryUtil.NULL) {
                throw new IllegalStateException("Failed to open the default OpenAL device.");
            }
            ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            context = ALC10.alcCreateContext(device, (IntBuffer) null);
            if (context == MemoryUtil.NULL) {
                throw new IllegalStateException("Failed to create OpenAL context.");
            }
            ALC10.alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);
            
        }
        catch( java.lang.Exception e )
        {
            return false;
        }

        AL.setCurrentProcess(null);
        ALC10.alcMakeContextCurrent(MemoryUtil.NULL);
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);

        return true;
    }

    





    @Override
    protected Channel createChannel( int type )
    {
        ChannelLWJGLOpenAL channel;
        IntBuffer ALSource;

        ALSource = BufferUtils.createIntBuffer( 1 );
        try
        {
            AL10.alGenSources( ALSource );
        }
        catch( java.lang.Exception e )
        {
            AL10.alGetError();
            return null;  
        }

        if( AL10.alGetError() != AL10.AL_NO_ERROR )
            return null;

        channel = new ChannelLWJGLOpenAL( type, ALSource );
        return channel;
    }

    



    @Override
    public void cleanup()
    {
        super.cleanup();

        Set<String> keys = bufferMap.keySet();
        Iterator<String> iter = keys.iterator();
        String filename;
        IntBuffer buffer;

        
        while( iter.hasNext() )
        {
            filename = iter.next();
            buffer = ALBufferMap.get( filename );
            if( buffer != null )
            {
                AL10.alDeleteBuffers( buffer );
                checkALError();
                buffer.clear();
            }
        }

        bufferMap.clear();
        AL.setCurrentProcess(null);
        ALC10.alcMakeContextCurrent(MemoryUtil.NULL);
        if (openAlContext != MemoryUtil.NULL) {
            ALC10.alcDestroyContext(openAlContext);
            openAlContext = MemoryUtil.NULL;
        }
        if (openAlDevice != MemoryUtil.NULL) {
            ALC10.alcCloseDevice(openAlDevice);
            openAlDevice = MemoryUtil.NULL;
        }

        bufferMap = null;
        listenerPositionAL = null;
        listenerOrientation = null;
        listenerVelocity = null;
    }

    




    @Override
    public boolean loadSound( FilenameURL filenameURL )
    {
        
        if( bufferMap == null )
        {
            bufferMap = new HashMap<String, SoundBuffer>();
            importantMessage( "Buffer Map was null in method 'loadSound'" );
        }
        
        if( ALBufferMap == null )
        {
            ALBufferMap = new HashMap<String, IntBuffer>();
            importantMessage( "Open AL Buffer Map was null in method" +
                    "'loadSound'" );
        }

        
        if( errorCheck( filenameURL == null,
                "Filename/URL not specified in method 'loadSound'" ) )
            return false;

        
        if( bufferMap.get( filenameURL.getFilename() ) != null )
            return true;

        ICodec codec = SoundSystemConfig.getCodec( filenameURL.getFilename() );
        if( errorCheck( codec == null, "No codec found for file '" +
                filenameURL.getFilename() +
                "' in method 'loadSound'" ) )
            return false;
        codec.reverseByteOrder( true );

        URL url = filenameURL.getURL();
        if( errorCheck( url == null, "Unable to open file '" +
                filenameURL.getFilename() +
                "' in method 'loadSound'" ) )
            return false;

        codec.initialize( url );
        SoundBuffer buffer = codec.readAll();
        codec.cleanup();
        codec = null;
        if( errorCheck( buffer == null,
                "Sound buffer null in method 'loadSound'" ) )
            return false;

        bufferMap.put( filenameURL.getFilename(), buffer );

        AudioFormat audioFormat = buffer.audioFormat;
        int soundFormat = 0;
        if( audioFormat.getChannels() == 1 )
        {
            if( audioFormat.getSampleSizeInBits() == 8 )
            {
                soundFormat = AL10.AL_FORMAT_MONO8;
            }
            else if( audioFormat.getSampleSizeInBits() == 16 )
            {
                soundFormat = AL10.AL_FORMAT_MONO16;
            }
            else
            {
                errorMessage( "Illegal sample size in method 'loadSound'" );
                return false;
            }
        }
        else if( audioFormat.getChannels() == 2 )
        {
            if( audioFormat.getSampleSizeInBits() == 8 )
            {
                soundFormat = AL10.AL_FORMAT_STEREO8;
            }
            else if( audioFormat.getSampleSizeInBits() == 16 )
            {
                soundFormat = AL10.AL_FORMAT_STEREO16;
            }
            else
            {
                errorMessage( "Illegal sample size in method 'loadSound'" );
                return false;
            }
        }
        else
        {
            errorMessage( "File neither mono nor stereo in method " +
                    "'loadSound'" );
            return false;
        }

        IntBuffer intBuffer = BufferUtils.createIntBuffer( 1 );
        AL10.alGenBuffers( intBuffer );
        if( errorCheck( AL10.alGetError() != AL10.AL_NO_ERROR,
                "alGenBuffers error when loading " +
                        filenameURL.getFilename() ) )
            return false;




        AL10.alBufferData( intBuffer.get( 0 ), soundFormat,
                (ByteBuffer) BufferUtils.createByteBuffer(
                        buffer.audioData.length ).put(
                        buffer.audioData ).flip(),
                (int) audioFormat.getSampleRate() );

        if( errorCheck( AL10.alGetError() != AL10.AL_NO_ERROR,
                "alBufferData error when loading " +
                        filenameURL.getFilename() ) )


            if( errorCheck( intBuffer == null,
                    "Sound buffer was not created for " +
                            filenameURL.getFilename() ) )
                return false;

        ALBufferMap.put( filenameURL.getFilename(), intBuffer );

        return true;
    }

    







    @Override
    public boolean loadSound( SoundBuffer buffer, String identifier )
    {
        
        if( bufferMap == null )
        {
            bufferMap = new HashMap<String, SoundBuffer>();
            importantMessage( "Buffer Map was null in method 'loadSound'" );
        }
        
        if( ALBufferMap == null )
        {
            ALBufferMap = new HashMap<String, IntBuffer>();
            importantMessage( "Open AL Buffer Map was null in method" +
                    "'loadSound'" );
        }

        
        if( errorCheck( identifier == null,
                "Identifier not specified in method 'loadSound'" ) )
            return false;

        
        if( bufferMap.get( identifier ) != null )
            return true;

        if( errorCheck( buffer == null,
                "Sound buffer null in method 'loadSound'" ) )
            return false;

        bufferMap.put( identifier, buffer );

        AudioFormat audioFormat = buffer.audioFormat;
        int soundFormat = 0;
        if( audioFormat.getChannels() == 1 )
        {
            if( audioFormat.getSampleSizeInBits() == 8 )
            {
                soundFormat = AL10.AL_FORMAT_MONO8;
            }
            else if( audioFormat.getSampleSizeInBits() == 16 )
            {
                soundFormat = AL10.AL_FORMAT_MONO16;
            }
            else
            {
                errorMessage( "Illegal sample size in method 'loadSound'" );
                return false;
            }
        }
        else if( audioFormat.getChannels() == 2 )
        {
            if( audioFormat.getSampleSizeInBits() == 8 )
            {
                soundFormat = AL10.AL_FORMAT_STEREO8;
            }
            else if( audioFormat.getSampleSizeInBits() == 16 )
            {
                soundFormat = AL10.AL_FORMAT_STEREO16;
            }
            else
            {
                errorMessage( "Illegal sample size in method 'loadSound'" );
                return false;
            }
        }
        else
        {
            errorMessage( "File neither mono nor stereo in method " +
                    "'loadSound'" );
            return false;
        }

        IntBuffer intBuffer = BufferUtils.createIntBuffer( 1 );
        AL10.alGenBuffers( intBuffer );
        if( errorCheck( AL10.alGetError() != AL10.AL_NO_ERROR,
                "alGenBuffers error when saving " +
                        identifier ) )
            return false;




        AL10.alBufferData( intBuffer.get( 0 ), soundFormat,
                (ByteBuffer) BufferUtils.createByteBuffer(
                        buffer.audioData.length ).put(
                        buffer.audioData ).flip(),
                (int) audioFormat.getSampleRate() );

        if( errorCheck( AL10.alGetError() != AL10.AL_NO_ERROR,
                "alBufferData error when saving " +
                        identifier ) )


            if( errorCheck( intBuffer == null,
                    "Sound buffer was not created for " +
                            identifier ) )
                return false;

        ALBufferMap.put( identifier, intBuffer );

        return true;
    }

    






    @Override
    public void unloadSound( String filename )
    {
        ALBufferMap.remove( filename );
        super.unloadSound( filename );
    }

    



    @Override
    public void setMasterVolume( float value )
    {
        super.setMasterVolume( value );

        AL10.alListenerf( AL10.AL_GAIN, value );
        checkALError();
    }

    












    @Override
    public void newSource( boolean priority, boolean toStream, boolean toLoop,
                           String sourcename, FilenameURL filenameURL, float x,
                           float y, float z, int attModel, float distOrRoll )
    {
        IntBuffer myBuffer = null;
        if( !toStream )
        {
            
            myBuffer = ALBufferMap.get( filenameURL.getFilename() );

            
            if( myBuffer == null )
            {
                if( !loadSound( filenameURL ) )
                {
                    errorMessage( "Source '" + sourcename + "' was not created "
                            + "because an error occurred while loading "
                            + filenameURL.getFilename() );
                    return;
                }
            }

            
            myBuffer = ALBufferMap.get( filenameURL.getFilename() );
            
            if( myBuffer == null )
            {
                errorMessage( "Source '" + sourcename + "' was not created "
                        + "because a sound buffer was not found for "
                        + filenameURL.getFilename() );
                return;
            }
        }
        SoundBuffer buffer = null;

        if( !toStream )
        {
            
            buffer = bufferMap.get( filenameURL.getFilename() );
            
            if( buffer == null )
            {
                if( !loadSound( filenameURL ) )
                {
                    errorMessage( "Source '" + sourcename + "' was not created "
                            + "because an error occurred while loading "
                            + filenameURL.getFilename() );
                    return;
                }
            }
            
            buffer = bufferMap.get( filenameURL.getFilename() );
            
            if( buffer == null )
            {
                errorMessage( "Source '" + sourcename + "' was not created "
                        + "because audio data was not found for "
                        + filenameURL.getFilename() );
                return;
            }
        }

        sourceMap.put( sourcename,
                new SourceLWJGLOpenAL( listenerPositionAL, myBuffer,
                        priority, toStream, toLoop,
                        sourcename, filenameURL, buffer, x,
                        y, z, attModel, distOrRoll,
                        false ) );
    }

    










    @Override
    public void rawDataStream( AudioFormat audioFormat, boolean priority,
                               String sourcename, float x, float y,
                               float z, int attModel, float distOrRoll )
    {
        sourceMap.put( sourcename,
                new SourceLWJGLOpenAL( listenerPositionAL, audioFormat,
                        priority, sourcename, x, y, z,
                        attModel, distOrRoll ) );
    }

    













    @Override
    public void quickPlay( boolean priority, boolean toStream, boolean toLoop,
                           String sourcename, FilenameURL filenameURL, float x,
                           float y, float z, int attModel, float distOrRoll,
                           boolean temporary )
    {
        IntBuffer myBuffer = null;
        if( !toStream )
        {
            
            myBuffer = ALBufferMap.get( filenameURL.getFilename() );
            
            if( myBuffer == null )
                loadSound( filenameURL );
            
            myBuffer = ALBufferMap.get( filenameURL.getFilename() );
            
            if( myBuffer == null )
            {
                errorMessage( "Sound buffer was not created for " +
                        filenameURL.getFilename() );
                return;
            }
        }

        SoundBuffer buffer = null;

        if( !toStream )
        {
            
            buffer = bufferMap.get( filenameURL.getFilename() );
            
            if( buffer == null )
            {
                if( !loadSound( filenameURL ) )
                {
                    errorMessage( "Source '" + sourcename + "' was not created "
                            + "because an error occurred while loading "
                            + filenameURL.getFilename() );
                    return;
                }
            }
            
            buffer = bufferMap.get( filenameURL.getFilename() );
            
            if( buffer == null )
            {
                errorMessage( "Source '" + sourcename + "' was not created "
                        + "because audio data was not found for "
                        + filenameURL.getFilename() );
                return;
            }
        }
        SourceLWJGLOpenAL s = new SourceLWJGLOpenAL( listenerPositionAL,
                myBuffer, priority,
                toStream, toLoop,
                sourcename, filenameURL,
                buffer, x, y, z,
                attModel, distOrRoll,
                false );

        sourceMap.put( sourcename, s );
        play( s );
        if( temporary )
            s.setTemporary( true );
    }

    



    @Override
    public void copySources( HashMap<String, Source> srcMap )
    {
        if( srcMap == null )
            return;
        Set<String> keys = srcMap.keySet();
        Iterator<String> iter = keys.iterator();
        String sourcename;
        Source source;

        
        if( bufferMap == null )
        {
            bufferMap = new HashMap<String, SoundBuffer>();
            importantMessage( "Buffer Map was null in method 'copySources'" );
        }
        
        if( ALBufferMap == null )
        {
            ALBufferMap = new HashMap<String, IntBuffer>();
            importantMessage( "Open AL Buffer Map was null in method" +
                    "'copySources'" );
        }

        
        sourceMap.clear();

        SoundBuffer buffer;
        
        while( iter.hasNext() )
        {
            sourcename = iter.next();
            source = srcMap.get( sourcename );
            if( source != null )
            {
                buffer = null;
                if( !source.toStream )
                {
                    loadSound( source.filenameURL );
                    buffer = bufferMap.get( source.filenameURL.getFilename() );
                }
                if( source.toStream || buffer != null )
                    sourceMap.put( sourcename, new SourceLWJGLOpenAL(
                            listenerPositionAL,
                            ALBufferMap.get(
                                    source.filenameURL.getFilename() ),
                            source, buffer ) );
            }
        }
    }

    





    @Override
    public void setListenerPosition( float x, float y, float z )
    {
        super.setListenerPosition( x, y, z );

        listenerPositionAL.put( 0, x );
        listenerPositionAL.put( 1, y );
        listenerPositionAL.put( 2, z );

        
        AL10.alListenerfv( AL10.AL_POSITION, listenerPositionAL );
        
        checkALError();
    }

    




    @Override
    public void setListenerAngle( float angle )
    {
        super.setListenerAngle( angle );

        listenerOrientation.put( 0, listener.lookAt.x );
        listenerOrientation.put( 2, listener.lookAt.z );

        
        AL10.alListenerfv( AL10.AL_ORIENTATION, listenerOrientation );
        
        checkALError();
    }

    








    @Override
    public void setListenerOrientation( float lookX, float lookY, float lookZ,
                                        float upX, float upY, float upZ )
    {
        super.setListenerOrientation( lookX, lookY, lookZ, upX, upY, upZ );
        listenerOrientation.put( 0, lookX );
        listenerOrientation.put( 1, lookY );
        listenerOrientation.put( 2, lookZ );
        listenerOrientation.put( 3, upX );
        listenerOrientation.put( 4, upY );
        listenerOrientation.put( 5, upZ );
        AL10.alListenerfv( AL10.AL_ORIENTATION, listenerOrientation );
        checkALError();
    }

    




    @Override
    public void setListenerData( ListenerData l )
    {
        super.setListenerData( l );

        listenerPositionAL.put( 0, l.position.x );
        listenerPositionAL.put( 1, l.position.y );
        listenerPositionAL.put( 2, l.position.z );
        AL10.alListenerfv( AL10.AL_POSITION, listenerPositionAL );
        checkALError();

        listenerOrientation.put( 0, l.lookAt.x );
        listenerOrientation.put( 1, l.lookAt.y );
        listenerOrientation.put( 2, l.lookAt.z );
        listenerOrientation.put( 3, l.up.x );
        listenerOrientation.put( 4, l.up.y );
        listenerOrientation.put( 5, l.up.z );
        AL10.alListenerfv( AL10.AL_ORIENTATION, listenerOrientation );
        checkALError();

        listenerVelocity.put( 0, l.velocity.x );
        listenerVelocity.put( 1, l.velocity.y );
        listenerVelocity.put( 2, l.velocity.z );
        AL10.alListenerfv( AL10.AL_VELOCITY, listenerVelocity );
        checkALError();
    }

    





    @Override
    public void setListenerVelocity( float x, float y, float z )
    {
        super.setListenerVelocity( x, y, z );

        listenerVelocity.put( 0, listener.velocity.x );
        listenerVelocity.put( 1, listener.velocity.y );
        listenerVelocity.put( 2, listener.velocity.z );
        AL10.alListenerfv( AL10.AL_VELOCITY, listenerVelocity );
    }

    


    @Override
    public void dopplerChanged()
    {
        super.dopplerChanged();

        AL10.alDopplerFactor( SoundSystemConfig.getDopplerFactor() );
        checkALError();
        AL10.alDopplerVelocity( SoundSystemConfig.getDopplerVelocity() );
        checkALError();
    }

    



    private boolean checkALError()
    {
        switch( AL10.alGetError() )
        {
            case AL10.AL_NO_ERROR:
                return false;
            case AL10.AL_INVALID_NAME:
                errorMessage( "Invalid name parameter." );
                return true;
            case AL10.AL_INVALID_ENUM:
                errorMessage( "Invalid parameter." );
                return true;
            case AL10.AL_INVALID_VALUE:
                errorMessage( "Invalid enumerated parameter value." );
                return true;
            case AL10.AL_INVALID_OPERATION:
                errorMessage( "Illegal call." );
                return true;
            case AL10.AL_OUT_OF_MEMORY:
                errorMessage( "Unable to allocate memory." );
                return true;
            default:
                errorMessage( "An unrecognized error occurred." );
                return true;
        }
    }

    



    public static boolean alPitchSupported()
    {
        return alPitchSupported( GET, XXX );
    }
    





    private static synchronized boolean alPitchSupported( boolean action,
                                                          boolean value )
    {
        if( action == SET )
            alPitchSupported = value;
        return alPitchSupported;
    }

    



    public static String getTitle()
    {
        return "LWJGL OpenAL";
    }

    



    public static String getDescription()
    {
        return "The LWJGL binding of OpenAL.  For more information, see " +
                "http://www.lwjgl.org";
    }

    



    @Override
    public String getClassName()
    {
        return "LibraryLWJGLOpenAL";
    }

    



    public static class Exception extends SoundSystemException
    {
        private static final long serialVersionUID = -7502452059037798035L;
        



        public static final int CREATE                           =  101;
        


        public static final int INVALID_NAME                     =  102;
        


        public static final int INVALID_ENUM                     =  103;
        


        public static final int INVALID_VALUE                     =  104;
        


        public static final int INVALID_OPERATION                 = 105;
        


        public static final int OUT_OF_MEMORY                    = 106;
        


        public static final int LISTENER                           = 107;
        


        public static final int NO_AL_PITCH                       = 108;

        




        public Exception( String message )
        {
            super( message );
        }

        





        public Exception( String message, int type )
        {
            super( message, type );
        }
    }
}
