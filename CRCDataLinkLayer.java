// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// =============================================================================


// =============================================================================
/**
 * @file   DumbDataLinkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   August 2018, original September 2004
 *
 * A data link layer that uses start/stop tags and byte packing to frame the
 * data, and that performs no error management.
 */
public class CRCDataLinkLayer extends DataLinkLayer {
// =============================================================================



    // =========================================================================
    /**
     * Embed a raw sequence of bytes into a framed sequence.
     *
     * @param  data The raw sequence of bytes to be framed.
     * @return A complete frame.
     */
    protected byte[] createFrame (byte[] data) {

        Queue<Byte> framingData = new LinkedList<Byte>();
        
        // Begin with the start tag.
        framingData.add(startTag);

        // Add each byte of original data and a byte with CRC calculation data.
        for (int i = 0; i < data.length; i += 1) {

            // If the current data byte is itself a metadata tag, then precede
            // it with an escape tag.
            byte currentByte = data[i];
            if ((currentByte == startTag) || (currentByte == stopTag) || (currentByte == escapeTag)) {
                framingData.add(escapeTag);
            }

            // Add the data byte itself.
            framingData.add(currentByte);

            //Get CRC Remainder and send it in next byte.
            byte byteCRC = getRemainder(currentByte);
            framingData.add(byteCRC);

        }

        // End with a stop tag.
        framingData.add(stopTag);

        // Convert to the desired byte array.
        byte[] framedData = new byte[framingData.size()];
        Iterator<Byte>  i = framingData.iterator();
        int             j = 0;
        while (i.hasNext()) {
            framedData[j++] = i.next();
        }

        return framedData;
	
    } // createFrame ()
    // =========================================================================


    
    // =========================================================================
    /**
     * Determine whether the received, buffered data constitutes a complete
     * frame.  If so, then remove the framing metadata and return the original
     * data.  Note that any data preceding an escaped start tag is assumed to be
     * part of a damaged frame, and is thus discarded.
     *
     * @return If the buffer contains a complete frame, the extracted, original
     * data; <code>null</code> otherwise.
     */
    protected byte[] processFrame () {

        //If there has been an error in a previous frame, do not accept any more frames.
        if(error){
            return null;
        }

        // Search for a start tag.  Discard anything prior to it.
        boolean        startTagFound = false;
        Iterator<Byte>             i = byteBuffer.iterator();
        while (!startTagFound && i.hasNext()) {
            byte current = i.next();
            if (current != startTag) {
            i.remove();
            } else {
            startTagFound = true;
            }
        }

        // If there is no start tag, then there is no frame. Set error to true.
        if (!startTagFound) {
            error = true;
            System.out.println("ERROR - No Stop Tag Found.");
            return null;
        }
        
        // Try to extract data while waiting for an unescaped stop tag.
        Queue<Byte> extractedBytes = new LinkedList<Byte>();
        boolean       stopTagFound = false;
        while (!stopTagFound && i.hasNext()) {

            // Grab the next byte.  If it is...
            //   (a) An escape tag: Skip over it and grab what follows as
            //                      literal data.
            //   (b) A stop tag:    Remove all processed bytes from the buffer and
            //                      end extraction.
            //   (c) A start tag:   All that precedes is damaged, so remove it
            //                      from the buffer and restart extraction.
            //   (d) Otherwise:     Take it as literal data.
            byte current = i.next();
            if (current == escapeTag) {

                if (i.hasNext()) {
                    // Take next byte as literal data
                    current = i.next();
                    // Check if current has a next byte - otherwise there is no check byte to checkCRC() with.
                    if(i.hasNext()){
                        // Check byte with CRC, if it's correct then add the bit, otherwise return null.
                        boolean correct = checkCRC(current, i);
                        if(correct){
                            extractedBytes.add(current);
                        } else {
                            System.out.println("ERROR - Data Byte Failed CRC Test.");
                            System.out.printf("Incorrect Data = %c\n", current);
                            error = true;
                            return null;
                        }
                    } else {
                        // Return null and then wait for the check byte to come through.
                        return null;
                    }
                    
                } else {
                    // An escape was the last byte available, so this is not a
                    // complete frame.
                    return null;
                } // if, else statement

            } else if (current == stopTag) {

                cleanBufferUpTo(i);
                stopTagFound = true;

            } else if (current == startTag) {

                cleanBufferUpTo(i);
                extractedBytes = new LinkedList<Byte>();

            } else {
                // Check if current has a next byte - otherwise there is no check byte to checkCRC() with.
                if(i.hasNext()){
                    // Check byte with CRC, if it's correct then add the bit, otherwise return null.
                    boolean correct = checkCRC(current, i);
                    if(correct){
                        extractedBytes.add(current);
                    } else {
                        System.out.println("ERROR - Data Byte Failed CRC Test.");
                        System.out.printf("Incorrect Data = %c\n", current);
                        error = true;
                        return null;
                    }
                } else {
                    // Return null and then wait for the check byte to come through.
                    return null;
                }

            } //if, else if, else if, else statement

        } // While loop

        // If there is no stop tag, then the frame is incomplete.
        if (!stopTagFound) {
            return null;
        }

        // Convert to the desired byte array.
        if (debug) {
            System.out.println("CRCDataLinkLayer.processFrame(): Got whole frame!");
        }
        byte[] extractedData = new byte[extractedBytes.size()];
        int                j = 0;
        i = extractedBytes.iterator();
        while (i.hasNext()) {
            extractedData[j] = i.next();
            if (debug) {
            System.out.printf("CRCDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
                    j,
                    extractedData[j]);
            }
            j += 1;
        }

        return extractedData;

    } // processFrame ()
    // ===============================================================



    // ===============================================================
    private void cleanBufferUpTo (Iterator<Byte> end) {

	Iterator<Byte> i = byteBuffer.iterator();
	while (i.hasNext() && i != end) {
	    i.next();
	    i.remove();
	}

    }
    // ===============================================================



    //================================================================
    @Override
	public void send (byte[] data) {

		// Call on the underlying physical layer to send the data.
		for(int j = 0; j < data.length; j+= 1){

			byte[] temp = {data[j]};

			byte[] framedData = createFrame(temp);
			for (int i = 0; i < framedData.length; i += 1) {
				transmit(framedData[i]);
			}

		}
	
    }
    //================================================================


    
    //================================================================
    public static boolean checkCRC(byte dataByte,Iterator<Byte> i){

        int data = (int) dataByte;
        if(!i.hasNext()){
            return false;
        }

        int byteCRC = (int)(i.next());
        data = (data << 4) ^ byteCRC;

        int current = 0;
        for(int j = BITS_PER_BYTE + 3; j >= 0; j--){
            boolean bit = testBit(data, j);
            current = injectBit(current, bit);

            if(testBit(current, generatorLength - 1)){
                current = current ^ generator;
            }
        }

        return current == 0;
    }
    //================================================================



    //================================================================
    public static int addCRC(int data){

        data = data << generatorLength - 1;

        int current = 0;
        for(int i = BITS_PER_BYTE + 3; i >= 0; i--){
            boolean bit = testBit(data, i);
            current = injectBit(current, bit);

            if(testBit(current, generatorLength - 1)){
                current = current ^ generator;
            }
        }

        return data ^ current;
    }
    //================================================================



    //================================================================
    public static int injectBit(int v, boolean bit){
        int newValue = 0;
        if(bit){
            newValue = 1;
        }
        v = v << 1;
        return (v | newValue);
    }
    //================================================================



    //================================================================
    public static boolean testBit(int v, int position){
        return ( v & (1 << position)) != 0;
    }
    //================================================================



    //================================================================
    public static byte getRemainder(byte dataByte){

        int data = (int)dataByte;
        data = data << generatorLength - 1;

        int current = 0;
        for(int i = BITS_PER_BYTE + 3; i >= 0; i--){
            boolean bit = testBit(data, i);
            current = injectBit(current, bit);

            if(testBit(current, generatorLength - 1)){
                current = current ^ generator;
            }
        }

        return (byte)current;
    }
    //================================================================



    // ===============================================================
    // DATA MEMBERS
    // ===============================================================



    // ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag  = (byte)'{';
    private final byte stopTag   = (byte)'}';
    private final byte escapeTag = (byte)'\\';
    private static final int generator = 0b10011;
    private static final int generatorLength = 5;
    private static boolean error = false;
    // ===============================================================



// ===================================================================
} // class CRCDataLinkLayer
// ===================================================================
