// =============================================================================
// IMPORTS

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
// =============================================================================


// =============================================================================
/**
 * @file   ParityDataLinkLayer.java
 * @author Scott F. Kaplan (sfkaplan@cs.amherst.edu)
 * @date   August 2018, original September 2004
 *
 * A data link layer that uses start/stop tags and byte packing to frame the
 * data, and that performs no error management.
 */
public class Parity2DataLinkLayer extends DataLinkLayer {
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
    
	// Add each byte of original data.
	for (int i = 0; i < data.length; i += 1) {

	    // If the current data byte is itself a metadata tag, then precede
	    // it with an escape tag.
        byte currentByte = data[i];

        //keep track of the number of bits of value one in the current byte
        
	    if ((currentByte == startTag) || (currentByte == stopTag) || (currentByte == escapeTag) || (currentByte == 0b1) || (currentByte == 0b0)) {

		    framingData.add(escapeTag);

	    }

	    // Add the data byte itself.
		framingData.add(currentByte);

		// Create the Parity byte
		byte parityByte = createParity(currentByte);

		// Add the parity byte itself.
		framingData.add(parityByte);

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

		//error = false;

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
	
		// If there is no start tag, then there is no frame.
		if (!startTagFound) {
			System.out.println("Error - no start tag");
			return null;
		}
		
		// Try to extract data while waiting for an unescaped stop tag.
		Queue<Byte> extractedBytes = new LinkedList<Byte>();
		boolean       stopTagFound = false;
		while (!stopTagFound && i.hasNext()) {

			error = false;
	
			// Grab the next byte.  If it is...
			//   (a) An escape tag: Skip over it and grab what follows as
			//                      literal data.
			//   (b) A stop tag:    Remove all processed bytes from the buffer and
			//                      end extraction.
			//   (c) A start tag:   All that precedes is damaged, so remove it
			//                      from the buffer and restart extraction.
			//   (d) Otherwise:     Take it as literal data.
			byte current = i.next();
			byte parity;
			if (current == escapeTag) {
				if (i.hasNext()) {
					current = i.next();
					if(i.hasNext()){
						parity = i.next();
						if(checkParity(current, parity)){
							extractedBytes.add(current);
						} else {
							System.out.printf("Incorrect Data = %c\n", current);
							System.out.println("Error - data and parity byte did not match...");
							error = true;
							return null;
						}
					} else {
						return null;
					}
				} else {
					// An escape was the last byte available, so this is not a
					// complete frame.
					return null;
				}
			} else if (current == stopTag) {
				cleanBufferUpTo(i);
				stopTagFound = true;
			} else if (current == startTag) {
				cleanBufferUpTo(i);
				extractedBytes = new LinkedList<Byte>();
			} else {
				if(i.hasNext()){
					parity = i.next();
					if(parity == startTag){
						System.out.println("Stop Tag Corrupted");
						cleanBufferUpTo(i);
						byteBuffer.add(startTag);
						extractedBytes = new LinkedList<Byte>();
					} else if(checkParity(current, parity)){
						extractedBytes.add(current);
					} else {
						error = true;
						System.out.printf("Incorrect Data = %c\n", current);
						System.out.println("Error - data and parity byte did not match...");
						return null;
					}
				} else {
					return null;
				}
			}
	
		}
	
		// If there is no stop tag, then the frame is incomplete.
		if (!stopTagFound) {
			return null;
		}
	
		// Convert to the desired byte array.
		if (debug) {
			System.out.println("Parity2DataLinkLayer.processFrame(): Got whole frame!");
		}
		byte[] extractedData = new byte[extractedBytes.size()];
		int                j = 0;
		i = extractedBytes.iterator();
		while (i.hasNext()) {
			extractedData[j] = i.next();
			if (debug) {
			System.out.printf("Parity2DataLinkLayer.processFrame():\tbyte[%d] = %c\n",
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



	// ===============================================================
	private static boolean checkParity(byte data, byte parity){
		int track = 0;
		int bits = (int)data;
		for(int j = 0; j < BITS_PER_BYTE; j++){
			if((bits & 0b1) == 0b1){
				track++;
			}
			bits = bits >> 1;
		}
		
		return (track % 2) == (int)parity;
	}
	//================================================================



	//================================================================
	public static byte createParity(byte data){
		int track = 0;
		int bits = (int)data;
		for(int j = 0; j < BITS_PER_BYTE; j++){
			if((bits & 0b1) == 0b1){
				track++;
			}
			bits = bits >> 1;
		}

		if(track % 2 == 1){
			return 0b1;
		} else {
			return 0b0;
		}
	}
	//================================================================



	//================================================================
	@Override
	public void send (byte[] data) {

		// Call on the underlying physical layer to send the data.
		for(int j = 0; j < data.length; j+= 1){
			//byte[] temp = {data[j], data[j+1], data[j+2], data[j+3]};
			byte[] temp = {data[j]};

			byte[] framedData = createFrame(temp);
			for (int i = 0; i < framedData.length; i += 1) {
				transmit(framedData[i]);
			}

		}
	
	}
	//================================================================



	//================================================================
	@Override
	public void receive (boolean bit) {

		// Add the new bit to the buffer.
		bitBuffer.add(bit);
	
		// If this bit completes a byte, then add it to the byte buffer.
		if (bitBuffer.size() >= BITS_PER_BYTE) {
	
			// Build up the byte from the bits...
			byte newByte = 0;
			for (int i = 0; i < BITS_PER_BYTE; i += 1) {
			bit = bitBuffer.remove();
			newByte = (byte)((newByte << 1) | (bit ? 1 : 0));
			}
	
			// ...and add it to the byte buffer.
			byteBuffer.add(newByte);
			if (debug) {
			System.out.printf("DataLinkLayer.receive(): Got new byte = %c\n",
					  newByte);
			}
	
			// Attempt to process the buffered bytes as a frame.  If a complete
			// frame is found and its contents extraction, deliver those
			// contents to the client.
			byte[] originalData = processFrame();
			if (originalData != null) {
				if (debug) {
					System.out.println("DataLinkLayer.receive(): Got a whole frame!");
				}
				client.receive(originalData);
			} 
			else if(error){
				byteBuffer = new LinkedList<Byte>();
				error = false;
			}
	
		}
	
	} // receive ()
	//================================================================



    // ===============================================================
    // DATA MEMBERS
    // ===============================================================



    // ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag  = (byte)'{';
    private final byte stopTag   = (byte)'}';
	private final byte escapeTag = (byte)'\\';
	protected boolean error = false;
	// ===============================================================
	
	public static void main(String[] args){
		
	}

// ===================================================================
} // class ParityDataLinkLayer
// ===================================================================
