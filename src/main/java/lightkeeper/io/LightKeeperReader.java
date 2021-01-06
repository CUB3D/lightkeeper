package lightkeeper.io;

import java.io.IOException;

import ghidra.app.util.bin.BinaryReader;

public class LightKeeperReader extends BinaryReader {

	protected LightKeeperByteProvider provider;
	
	public LightKeeperReader(LightKeeperByteProvider provider) {		
		super(provider, true);
		this.provider = provider;
	}

	public String readLine() throws IOException {
		long index = 0;
		StringBuilder buffer = new StringBuilder();
		long len = provider.length();
		while (true) {
			if (index == len)
				throw new IOException("Read end of input");
				
			char c = (char)provider.readByte(index++);
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
