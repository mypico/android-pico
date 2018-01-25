package org.mypico.android.comms;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.bluetooth.BluetoothSocket;

import org.mypico.jpico.comms.CombinedVerifierProxy;
import org.mypico.jpico.comms.MessageSerializer;

/**
 * @author Alexander Dalgleish <amd96@cam.ac.uk>
 * @author Claudio Dettoni <cd611@cam.ac.uk>
 */
public class SigmaProxy extends CombinedVerifierProxy {

    private final static Logger LOGGER =
        LoggerFactory.getLogger(SigmaProxy.class.getSimpleName());

    private final static int DEFAULT_BT_TIMEOUT_MS = 20000;

    private final BluetoothSocket channel;

    private IOException exception = null;
    private byte[] b = null;
    private boolean finished;
    private int timeout;

    public SigmaProxy(BluetoothSocket channel, MessageSerializer serializer) {
        super(serializer);
        this.channel = channel;
        this.timeout = DEFAULT_BT_TIMEOUT_MS;
    }

    @Override
    public void setTimeout(int timeout) {
        LOGGER.debug("Settings timeout {} miliseconds", timeout);
        this.timeout = timeout;
    }

    @Override
    protected byte[] getResponse(byte[] serializedMessage) throws IOException {
        writeMessage(serializedMessage);
        return readMessage();
    }

    @Override
    protected void writeMessage(byte[] serializedMessage) throws IOException {
        final DataOutputStream dos = new DataOutputStream(
            new BufferedOutputStream(channel.getOutputStream()));

        final int numBytesInMessage = serializedMessage.length;
        LOGGER.debug("Writing serialised message of {} bytes...", numBytesInMessage);
        dos.writeInt(numBytesInMessage);
        IOUtils.write(serializedMessage, dos);
        dos.flush();
        LOGGER.debug("Message written");
    }

    @Override
    protected byte[] readMessage() throws IOException {
        exception = null;
        b = null;
        finished = false;

        LOGGER.debug("Starting thread to read message");

        Thread readThread = new Thread(new Runnable() {
            public void run() {
                try {
                    // Read the response from the socket
                    final DataInputStream dis = new DataInputStream(channel.getInputStream());

                    final int numBytesInMessage = dis.readInt();
                    LOGGER.debug("Reading serialised message of {} bytes...", numBytesInMessage);
                    b = IOUtils.toByteArray(dis, numBytesInMessage);
                    LOGGER.debug("Message read");
                    synchronized (SigmaProxy.this) {
                        finished = true;
                        SigmaProxy.this.notify();
                    }
                } catch (IOException e) {
                    exception = e;
                }
            }
        });

        readThread.start();
        try {
            synchronized (this) {
                if (readThread.isAlive()) {
                    this.wait(timeout);
                }
            }

            if (!finished) {
                throw new IOException("Bluetooth timeout");
            } else if (exception != null) {
                throw exception;
            } else {
                assert b != null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new IOException("Error waiting for thread");
        }

        return b;
    }
}
