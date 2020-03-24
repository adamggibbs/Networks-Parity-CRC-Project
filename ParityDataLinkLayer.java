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
public class ParityDataLinkLayer extends DataLinkLayer {
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
	
		// If there is no start tag, then there is no frame.
		if (!startTagFound) {
			System.out.println("Error - no start tag");
			error = true;
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
			
			//define a byte to store the parity byte when a data byte is found.
			byte parity;

			if (current == escapeTag) {

				if (i.hasNext()) {
					current = i.next();
					// Check to see if the data byte has a following byte. If so store it as its parity byte.
					if(i.hasNext()){
						parity = i.next();
						// Check the data byte with the parity byte, if it's correct then add the bit, otherwise return null.
						if(checkParity(current, parity)){
							extractedBytes.add(current);
						} else {
							System.out.println("Error - data and parity byte did not match...");
							System.out.printf("Incorrect Data = %c\n", current);
							error = true;
							return null;
						}
					} else {
						// Return null and then wait for the parity byte to come through.
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
				// Check to see if the data byte has a following byte. If so store it as its parity byte
				if(i.hasNext()){
					parity = i.next();
					// if the parity byte is a start tag, then the previous byte is a corrupted stop tag. Throw an error.
					// if the parity byte is a normal parity byte, use it to check the data byte.
					if(parity == startTag){
						error = true;
						System.out.println("Stop Tag Corrupted");
						return null;
					} else if(checkParity(current, parity)){
						extractedBytes.add(current);
					} else {
						error = true;
						System.out.println("Error - data and parity byte did not match...");
						System.out.printf("Incorrect Data = %c\n", current);
						return null;
					}
				} else {
					// Return null and then wait for the parity byte to come through.
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
			System.out.println("ParityDataLinkLayer.processFrame(): Got whole frame!");
		}
		byte[] extractedData = new byte[extractedBytes.size()];
		int                j = 0;
		i = extractedBytes.iterator();
		while (i.hasNext()) {
			extractedData[j] = i.next();
			if (debug) {
			System.out.printf("ParityDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
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

			byte[] temp = {data[j]};

			byte[] framedData = createFrame(temp);
			for (int i = 0; i < framedData.length; i += 1) {
				transmit(framedData[i]);
			}

		}
	
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
	protected boolean error = false;
	// ===============================================================
	


// ===================================================================
} // class ParityDataLinkLayer
// ===================================================================
