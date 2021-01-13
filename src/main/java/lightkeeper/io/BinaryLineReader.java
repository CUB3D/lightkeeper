package lightkeeper.io;

import java.io.IOException;

import ghidra.app.util.bin.BinaryReader;

public class BinaryLineReader extends BinaryReader {

	protected CountedByteProvider provider;
	
	public BinaryLineReader(CountedByteProvider provider) {		
		super(provider, true);
		this.provider = provider;
	}

	public String readLine() throws IOException {		
		StringBuilder buffer = new StringBuilder();		
		while (true) {				
			char c = (char)this.readNextByte();
			if (isLineEnd(c))
				break;
			
			if (!isPrintable(c))
				throw new IOException(String.format("Invalid character at position: %d", provider.getPosition()));
						
			buffer.append(c);			
		}
		return buffer.toString();
	}
	
	private static boolean isPrintable(char c) {
		if (c < ' ')
			return false;
		if (c > '~')
			return false;
		return true;					
	}
	
	private static boolean isLineEnd(char c) {
		if (c == '\n')
			return true;
		return false;
	}
}
