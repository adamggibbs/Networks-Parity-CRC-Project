README.txt

For the two new Data Link Layers I wrote:
- They create frames that contain a single data byte and its corresponding meta data.
    - Parity bits follow each data byte as their own byte
    - The extra bits added at the end of a data byte in the CRC method are sent in another byte
      following the data byte and is added on to the data byte upon reception in the checkCRC() method. 
      
- They detect errors and print an error message containing a brief description of what the error was.
- If the error was caught due to a parity byte or CRC calculation, the "incorrect data" is also printed.
- Once an error is detected, no following frames are accepted by the data link Layers.
- Even if there's an error, all previously successfully received frames will be displayed.

**I also included another ParityDataLinkLayer titled Parity2DataLinkLayer.java that accepts and displays all 
  frames successfully sent even after an error before it had been detected. The other idea to throw out frames
  made more sense but I already wrote the other class too so I decided to include it. 