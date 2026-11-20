package paulscode.sound.libraries;

import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import javax.sound.sampled.AudioFormat;


import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import paulscode.sound.Channel;
import paulscode.sound.FilenameURL;
import paulscode.sound.Source;
import paulscode.sound.SoundBuffer;
import paulscode.sound.SoundSystemConfig;

/**
 * The SourceLWJGLOpenAL class provides an interface to the lwjgl binding of OpenAL.
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
public class SourceLWJGLOpenAL extends Source
{



    private ChannelLWJGLOpenAL channelOpenAL = (ChannelLWJGLOpenAL) channel;
    




    private IntBuffer myBuffer;
    



    private FloatBuffer listenerPosition;
    



    private FloatBuffer sourcePosition;
    



    private FloatBuffer sourceVelocity;
    

















    public SourceLWJGLOpenAL( FloatBuffer listenerPosition, IntBuffer myBuffer,
                              boolean priority, boolean toStream,
                              boolean toLoop, String sourcename,
                              FilenameURL filenameURL, SoundBuffer soundBuffer,
                              float x, float y, float z, int attModel,
                              float distOrRoll, boolean temporary )
    {
        super( priority, toStream, toLoop, sourcename, filenameURL, soundBuffer,
               x, y, z, attModel, distOrRoll, temporary );
        if( codec != null )
            codec.reverseByteOrder( true );
        this.listenerPosition = listenerPosition;
        this.myBuffer = myBuffer;
        libraryType = LibraryLWJGLOpenAL.class;
        pitch = 1.0f;
        resetALInformation();
    }
    







    public SourceLWJGLOpenAL( FloatBuffer listenerPosition, IntBuffer myBuffer,
                              Source old, SoundBuffer soundBuffer )
    {
        super( old, soundBuffer );
        if( codec != null )
            codec.reverseByteOrder( true );
        this.listenerPosition = listenerPosition;
        this.myBuffer = myBuffer;
        libraryType = LibraryLWJGLOpenAL.class;
        pitch = 1.0f;
        resetALInformation();
    }

    












    public SourceLWJGLOpenAL( FloatBuffer listenerPosition,
                              AudioFormat audioFormat, boolean priority,
                              String sourcename, float x, float y, float z,
                              int attModel, float distOrRoll )
    {
        super( audioFormat, priority, sourcename, x, y, z, attModel,
               distOrRoll );
        this.listenerPosition = listenerPosition;
        libraryType = LibraryLWJGLOpenAL.class;
        pitch = 1.0f;
        resetALInformation();
    }




    @Override
    public void cleanup()
    {
        
        super.cleanup();
    }
    


















    public void changeSource( FloatBuffer listenerPosition, IntBuffer myBuffer,
                              boolean priority, boolean toStream,
                              boolean toLoop, String sourcename,
                              FilenameURL filenameURL, SoundBuffer soundBuffer,
                              float x, float y, float z, int attModel,
                              float distOrRoll, boolean temporary )
    {
        super.changeSource( priority, toStream, toLoop, sourcename,
                            filenameURL, soundBuffer, x, y, z, attModel,
                            distOrRoll, temporary );
        this.listenerPosition = listenerPosition;
        this.myBuffer = myBuffer;
        pitch = 1.0f;
        resetALInformation();
    }
    







    @Override
    public boolean incrementSoundSequence()
    {
        if( !toStream )
        {
            errorMessage( "Method 'incrementSoundSequence' may only be used " +
                          "for streaming sources." );
            return false;
        }
        synchronized( soundSequenceLock )
        {
            if( soundSequenceQueue != null && soundSequenceQueue.size() > 0 )
            {
                filenameURL = soundSequenceQueue.remove( 0 );
                if( codec != null )
                    codec.cleanup();
                codec = SoundSystemConfig.getCodec( filenameURL.getFilename() );
                if( codec != null )
                {
                    codec.reverseByteOrder( true );
                    if( codec.getAudioFormat() == null )
                        codec.initialize( filenameURL.getURL() );

                    AudioFormat audioFormat = codec.getAudioFormat();

                    if( audioFormat == null )
                    {
                        errorMessage( "Audio Format null in method " +
                                      "'incrementSoundSequence'" );
                        return false;
                    }

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
                            errorMessage( "Illegal sample size in method " +
                                          "'incrementSoundSequence'" );
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
                            errorMessage( "Illegal sample size in method " +
                                          "'incrementSoundSequence'" );
                            return false;
                        }
                    }
                    else
                    {
                        errorMessage( "Audio data neither mono nor stereo in " +
                                      "method 'incrementSoundSequence'" );
                        return false;
                    }

                    
                    channelOpenAL.setFormat( soundFormat,
                                            (int) audioFormat.getSampleRate() );
                    preLoad = true;
                }
                return true;
            }
        }
        return false;
    }




    @Override
    public void listenerMoved()
    {
        positionChanged();
    }
    






    @Override
    public void setPosition( float x, float y, float z )
    {
        super.setPosition( x, y, z );
        
        
        if( sourcePosition == null )
            resetALInformation();
        else
            positionChanged();
        
        
        sourcePosition.put( 0, x );
        sourcePosition.put( 1, y );
        sourcePosition.put( 2, z );
        
        
        if( channel != null && channel.attachedSource == this &&
            channelOpenAL != null && channelOpenAL.ALSource != null )
        {
            
            AL10.alSourcefv( channelOpenAL.ALSource.get( 0 ), AL10.AL_POSITION,
                           sourcePosition );
            checkALError();
        }
    }
    



    @Override
    public void positionChanged()
    {
        calculateDistance();
        calculateGain();
        
        if( channel != null && channel.attachedSource == this &&
            channelOpenAL != null && channelOpenAL.ALSource != null )
        {
            AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                            AL10.AL_GAIN, (gain * sourceVolume
                                           * (float) Math.abs( fadeOutGain )
                                           * fadeInGain) );
            checkALError();
        }
        checkPitch();
    }
    



    private void checkPitch()
    {
        if( channel != null && channel.attachedSource == this &&
            LibraryLWJGLOpenAL.alPitchSupported() && channelOpenAL != null &&
            channelOpenAL.ALSource != null )
        {
            AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                          AL10.AL_PITCH, pitch );
            checkALError();
        }
    }





    @Override
    public void setLooping( boolean lp )
    {
        super.setLooping( lp );
        
        
        if( channel != null && channel.attachedSource == this &&
            channelOpenAL != null && channelOpenAL.ALSource != null )
        {
            if( lp )
                AL10.alSourcei( channelOpenAL.ALSource.get( 0 ),
                                AL10.AL_LOOPING, AL10.AL_TRUE );
            else
                AL10.alSourcei( channelOpenAL.ALSource.get( 0 ),
                                AL10.AL_LOOPING, AL10.AL_FALSE );
            checkALError();
        }
    }
    




    @Override
    public void setAttenuation( int model )
    {
        super.setAttenuation( model );
        
        if( channel != null && channel.attachedSource == this &&
            channelOpenAL != null && channelOpenAL.ALSource != null )
        {
            
            if( model == SoundSystemConfig.ATTENUATION_ROLLOFF )
                AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                                AL10.AL_ROLLOFF_FACTOR, distOrRoll );
            else
                AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                                AL10.AL_ROLLOFF_FACTOR, 0.0f );
            checkALError();
        }
    }
    





    @Override
    public void setDistOrRoll( float dr)
    {
        super.setDistOrRoll( dr );
        
        if( channel != null && channel.attachedSource == this &&
            channelOpenAL != null && channelOpenAL.ALSource != null )
        {
            
            if( attModel == SoundSystemConfig.ATTENUATION_ROLLOFF )
                AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                                AL10.AL_ROLLOFF_FACTOR, dr );
            else
                AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                                AL10.AL_ROLLOFF_FACTOR, 0.0f );
            checkALError();
        }
    }







    @Override
    public void setVelocity( float x, float y, float z )
    {
        super.setVelocity( x, y, z );

        sourceVelocity = BufferUtils.createFloatBuffer( 3 ).put( new float[]
                                                                  { x, y, z } );
        sourceVelocity.flip();
        
        if( channel != null && channel.attachedSource == this &&
            channelOpenAL != null && channelOpenAL.ALSource != null )
        {
            AL10.alSourcefv( channelOpenAL.ALSource.get( 0 ),
                          AL10.AL_VELOCITY, sourceVelocity );
            checkALError();
        }
    }
    




    @Override
    public void setPitch( float value )
    {
        super.setPitch( value );
        checkPitch();
    }





    @Override
    public void play( Channel c )
    {
        if( !active() )
        {
            if( toLoop )
                toPlay = true;
            return;
        }
        
        if( c == null )
        {
            errorMessage( "Unable to play source, because channel was null" );
            return;
        }
        
        boolean newChannel = (channel != c);
        if( channel != null && channel.attachedSource != this )
            newChannel = true;

        boolean wasPaused = paused();
        
        super.play( c );
        
        channelOpenAL = (ChannelLWJGLOpenAL) channel;
        
        
        
        if( newChannel )
        {
            setPosition( position.x, position.y, position.z );
            checkPitch();
            
            
            if( channelOpenAL != null && channelOpenAL.ALSource != null )
            {
                if( LibraryLWJGLOpenAL.alPitchSupported() )
                {
                    AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                                  AL10.AL_PITCH, pitch );
                    checkALError();
                }
                AL10.alSourcefv( channelOpenAL.ALSource.get( 0 ),
                               AL10.AL_POSITION, sourcePosition );
                checkALError();

                AL10.alSourcefv( channelOpenAL.ALSource.get( 0 ),
                               AL10.AL_VELOCITY, sourceVelocity );
                
                checkALError();

                if( attModel == SoundSystemConfig.ATTENUATION_ROLLOFF )
                    AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                                    AL10.AL_ROLLOFF_FACTOR, distOrRoll );
                else
                    AL10.alSourcef( channelOpenAL.ALSource.get( 0 ),
                                    AL10.AL_ROLLOFF_FACTOR, 0.0f );
                checkALError();

                if( toLoop && (!toStream) )
                    AL10.alSourcei( channelOpenAL.ALSource.get( 0 ),
                                    AL10.AL_LOOPING, AL10.AL_TRUE );
                else
                    AL10.alSourcei( channelOpenAL.ALSource.get( 0 ),
                                    AL10.AL_LOOPING, AL10.AL_FALSE );
                checkALError();
            }
            if( !toStream )
            {
                
                
                if( myBuffer == null )
                {
                    errorMessage( "No sound buffer to play" );
                    return;
                }
                
                channelOpenAL.attachBuffer( myBuffer );
            }
        }
        
        
        if( !playing() )
        {
            if( toStream && !wasPaused )
            {
                if( codec == null )
                {
                    errorMessage( "Decoder null in method 'play'" );
                    return;
                }
                if( codec.getAudioFormat() == null )
                    codec.initialize( filenameURL.getURL() );

                AudioFormat audioFormat = codec.getAudioFormat();

                if( audioFormat == null )
                {
                    errorMessage( "Audio Format null in method 'play'" );
                    return;
                }

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
                        errorMessage( "Illegal sample size in method 'play'" );
                        return;
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
                        errorMessage( "Illegal sample size in method 'play'" );
                        return;
                    }
                }
                else
                {
                    errorMessage( "Audio data neither mono nor stereo in " +
                                  "method 'play'" );
                    return;
                }

                
                channelOpenAL.setFormat( soundFormat,
                                            (int) audioFormat.getSampleRate() );
                preLoad = true;
            }
            channel.play();
            if( pitch != 1.0f )
                checkPitch();
        }
    }
    




    @Override
    public boolean preLoad()
    {
        if( codec == null )
            return false;
        
        codec.initialize( filenameURL.getURL() );
        LinkedList<byte[]> preLoadBuffers = new LinkedList<byte[]>();
        for( int i = 0; i < SoundSystemConfig.getNumberStreamingBuffers(); i++ )
        {
            soundBuffer = codec.read();

            if( soundBuffer == null || soundBuffer.audioData == null )
                break;

            preLoadBuffers.add( soundBuffer.audioData );
        }
        positionChanged();
        
        channel.preLoadBuffers( preLoadBuffers );        

        preLoad = false;
        return true;
    }
    



    private void resetALInformation()
    {
        
        sourcePosition = BufferUtils.createFloatBuffer( 3 ).put( 
            new float[] { position.x, position.y, position.z } );
        sourceVelocity = BufferUtils.createFloatBuffer( 3 ).put(
            new float[] { velocity.x, velocity.y, velocity.z } );

        
        sourcePosition.flip();
        sourceVelocity.flip();
        
        positionChanged();
    }
    



    private void calculateDistance()
    {
        if( listenerPosition != null )
        {
            
            double dX = position.x - listenerPosition.get( 0 );
            double dY = position.y - listenerPosition.get( 1 );
            double dZ = position.z - listenerPosition.get( 2 );
            distanceFromListener = (float) Math.sqrt( dX*dX + dY*dY + dZ*dZ );
        }
    }
    




    private void calculateGain()
    {
        
        if( attModel == SoundSystemConfig.ATTENUATION_LINEAR )
        {
            if( distanceFromListener <= 0 )
            {
                gain = 1.0f;
            }
            else if( distanceFromListener >= distOrRoll )
            {
                gain = 0.0f;
            }
            else
            {
                gain = 1.0f - (distanceFromListener / distOrRoll);
            }
            if( gain > 1.0f )
                gain = 1.0f;
            if( gain < 0.0f )
                gain = 0.0f;
        }
        else
        {
            gain = 1.0f;
        }
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
}
