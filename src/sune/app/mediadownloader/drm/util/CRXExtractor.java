package sune.app.mediadownloader.drm.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import sune.app.mediadown.util.NIO;

// Format specification sources:
// https://www.dre.vanderbilt.edu/~schmidt/android/android-4.0/external/chromium/chrome/common/extensions/docs/crx.html
// https://gromnitsky.blogspot.com/2019/04/crx3.html
// https://github.com/chromium/chromium/blob/e15cfb65a092b26224d6bdc685ed2aa9f41f7eb6/components/crx_file/crx3.proto
public class CRXExtractor {
	
	private static final int MAGIC = 0x43723234; // ASCII: Cr24
	private static final int VERSION = 3;
	
	private final BufferedInputStream stream;
	private int version;
	
	private boolean littleEndian = false;
	
	public CRXExtractor(Path file) throws IOException {
		stream = new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ));
	}
	
	private final void reverse(byte[] bytes) {
		for(int i = 0, l = bytes.length, k = l >> 1; i < k; ++i) {
			byte t = bytes[i];
			bytes[i] = bytes[l - i - 1];
			bytes[l - i - 1] = t;
		}
	}
	
	private final byte[] read(int n) throws IOException {
		byte[] arr = new byte[n];
		byte[] buf = new byte[Math.min(n, 8192)];
		int read = -1;
		for(int off = 0; (read = stream.read(buf, 0, Math.min(buf.length, n - off))) >= 0 && off < n;) {
			System.arraycopy(buf, 0, arr, off, read);
			off += read;
		}
		if(read == -1)
			throw new EOFException();
		if(littleEndian)
			reverse(arr);
		return arr;
	}
	
	private final byte[] readTillEOF() throws IOException {
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			byte[] buf = new byte[8192];
			for(int read; (read = stream.read(buf, 0, buf.length)) >= 0;) {
				baos.write(buf, 0, read);
			}
			byte[] arr = baos.toByteArray();
			if(littleEndian)
				reverse(arr);
			return arr;
		}
	}
	
	private final void skip(int n) throws IOException {
		byte[] buf = new byte[Math.min(n, 8192)];
		int read = -1;
		for(int off = 0; (read = stream.read(buf, 0, Math.min(buf.length, n - off))) >= 0 && off < n; off += read);
		if(read == -1)
			throw new EOFException();
	}
	
	private final long bytesToLong(byte[] bytes) {
		int i = 0, l = Math.min(bytes.length, 8);
		for(int k = 0, p = (l - 1) * 8; k < l; ++k, p -= 8) {
			i |= (bytes[k] & 0xff) << p;
		}
		return i;
	}
	
	private final int bytesToInt(byte[] bytes) {
		return (int) (bytesToLong(bytes) & 0xffffffff);
	}
	
	private final void readMagic() throws IOException {
		int magic = bytesToInt(read(4));
		if(magic != MAGIC)
			throw new IllegalStateException("Not a CRX file.");
	}
	
	private final int readVersion() throws IOException {
		return bytesToInt(read(4));
	}
	
	private final long readLength() throws IOException {
		return bytesToLong(read(4));
	}
	
	private final void extractZIP(byte[] bytes, Path output) throws IOException {
		NIO.createDir(output);
		try(ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
			for(ZipEntry entry; (entry = stream.getNextEntry()) != null;) {
				Path path = output.resolve(entry.getName());
				if(entry.isDirectory()) {
					NIO.createDir(path);
				} else {
					try(OutputStream out = Files.newOutputStream(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
						stream.transferTo(out);
					}
				}
			}
		}
	}
	
	public void extractNoCheck(Path output) throws IOException {
		readMagic();
		littleEndian = true;
		version = readVersion();
		if(version != VERSION)
			throw new IllegalStateException("Unsupported version: " + version);
		// Skip the "proofs" (public keys and signatures) header
		long headerLen = readLength();
		skip((int) headerLen);
		// Read the ZIP content
		littleEndian = false;
		extractZIP(readTillEOF(), output);
	}
}