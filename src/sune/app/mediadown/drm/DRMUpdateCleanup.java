package sune.app.mediadown.drm;

import java.nio.file.Path;
import java.util.List;

import sune.app.mediadown.update.Version;
import sune.app.mediadown.util.NIO;

/**
 * Used for cleaning up files and directories when the plugin is updated.
 * @author Sune
 */
public final class DRMUpdateCleanup {
	
	private static final CleanupJob[] jobs = { V2.instance() };
	
	// Forbid anyone to create an instance of this class
	private DRMUpdateCleanup() {
	}
	
	public static final void cleanup(Version current, Version previous) throws Exception {
		if(previous.compareTo(current) >= 0) {
			return; // Nothing to do
		}
		
		for(CleanupJob job : jobs) {
			if(!job.canApply(current, previous)) {
				continue;
			}
			
			job.cleanup();
		}
	}
	
	private static interface CleanupJob {
		
		void cleanup() throws Exception;
		boolean canApply(Version current, Version previous);
	}
	
	private static final class V2 implements CleanupJob {
		
		private static V2 instance;
		
		private final Version minVersion = Version.ZERO;
		private final Version maxVersion = Version.of("00.02.09-0001");
		
		private V2() {}
		
		public static final V2 instance() {
			if(instance == null) {
				instance = new V2();
			}
			
			return instance;
		}
		
		private final List<String> libraries() {
			return List.of(
				"lib/drm/gluegen-rt.jar",
				"lib/drm/gluegen-rt-natives-windows-amd64.jar",
				"lib/drm/joal-2.3.2.jar",
				"lib/drm/org.eclipse.swt.win32.win32.x86_64-3.118.0.jar",
				"lib/drm/jogl-all.jar",
				"lib/drm/jogl-all-natives-windows-amd64.jar",
				"lib/drm/jcef.jar",
				"lib/drm/proxy/activation-1.1.1.jar",
				"lib/drm/proxy/alpn-api-1.1.2.v20150522.jar",
				"lib/drm/proxy/org.osgi.core-4.3.1.jar",
				"lib/drm/proxy/log4j-api-2.6.2.jar",
				"lib/drm/proxy/javax.jms-api-2.0.1.jar",
				"lib/drm/proxy/javax.mail-1.6.2.jar",
				"lib/drm/proxy/log4j-1.2.17.jar",
				"lib/drm/proxy/slf4j-api-1.7.24.jar",
				"lib/drm/proxy/barchart-udt-bundle-2.3.0.jar",
				"lib/drm/proxy/bcprov-jdk15on-1.51.jar",
				"lib/drm/proxy/bcpkix-jdk15on-1.51.jar",
				"lib/drm/proxy/commons-lang3-3.5.jar",
				"lib/drm/proxy/javax.servlet-api-4.0.1.jar",
				"lib/drm/proxy/commons-logging-1.2.jar",
				"lib/drm/proxy/commons-cli-1.3.1.jar",
				"lib/drm/proxy/commons-io-2.11.0.jar",
				"lib/drm/proxy/dnsjava-2.1.3.jar",
				"lib/drm/proxy/dnssec4j-0.1.6.jar",
				"lib/drm/proxy/error_prone_annotations-2.11.0.jar",
				"lib/drm/proxy/jsr305-3.0.2.jar",
				"lib/drm/proxy/guava-20.0.jar",
				"lib/drm/proxy/javassist-3.19.0-GA.jar",
				"lib/drm/proxy/jboss-modules-1.1.0.Beta1.jar",
				"lib/drm/proxy/jboss-marshalling-1.4.11.Final.jar",
				"lib/drm/proxy/jzlib-1.1.3.jar",
				"lib/drm/proxy/npn-api-1.1.1.v20141010.jar",
				"lib/drm/proxy/protobuf-java-2.5.0.jar",
				"lib/drm/proxy/rxtx-2.1.7.jar",
				"lib/drm/proxy/netty-tcnative-1.1.33.Fork26.jar",
				"lib/drm/proxy/netty-all-4.0.44.Final.jar",
				"lib/drm/proxy/littleproxy-1.1.2.jar",
				"lib/drm/proxy/littleproxy-mitm-1.1.0.jar"
			);
		}
		
		private final List<String> resources() {
			return List.of(
				"resources/binary/drm/md-virtual-audio.dll",
				"resources/binary/drm/SoundVolumeView.exe",
				"resources/binary/drm/windows-kill.exe"
			);
		}
		
		private final List<String> cef() {
			return List.of(
				"lib/drm/cef/chrome_100_percent.pak",
				"lib/drm/cef/chrome_200_percent.pak",
				"lib/drm/cef/chrome_elf.dll",
				"lib/drm/cef/d3dcompiler_47.dll",
				"lib/drm/cef/icudtl.dat",
				"lib/drm/cef/jcef.dll",
				"lib/drm/cef/jcef_helper.exe",
				"lib/drm/cef/libcef.dll",
				"lib/drm/cef/libEGL.dll",
				"lib/drm/cef/libGLESv2.dll",
				"lib/drm/cef/locales/af.pak",
				"lib/drm/cef/locales/am.pak",
				"lib/drm/cef/locales/ar.pak",
				"lib/drm/cef/locales/bg.pak",
				"lib/drm/cef/locales/bn.pak",
				"lib/drm/cef/locales/ca.pak",
				"lib/drm/cef/locales/cs.pak",
				"lib/drm/cef/locales/da.pak",
				"lib/drm/cef/locales/de.pak",
				"lib/drm/cef/locales/el.pak",
				"lib/drm/cef/locales/en-GB.pak",
				"lib/drm/cef/locales/en-US.pak",
				"lib/drm/cef/locales/es-419.pak",
				"lib/drm/cef/locales/es.pak",
				"lib/drm/cef/locales/et.pak",
				"lib/drm/cef/locales/fa.pak",
				"lib/drm/cef/locales/fi.pak",
				"lib/drm/cef/locales/fil.pak",
				"lib/drm/cef/locales/fr.pak",
				"lib/drm/cef/locales/gu.pak",
				"lib/drm/cef/locales/he.pak",
				"lib/drm/cef/locales/hi.pak",
				"lib/drm/cef/locales/hr.pak",
				"lib/drm/cef/locales/hu.pak",
				"lib/drm/cef/locales/id.pak",
				"lib/drm/cef/locales/it.pak",
				"lib/drm/cef/locales/ja.pak",
				"lib/drm/cef/locales/kn.pak",
				"lib/drm/cef/locales/ko.pak",
				"lib/drm/cef/locales/lt.pak",
				"lib/drm/cef/locales/lv.pak",
				"lib/drm/cef/locales/ml.pak",
				"lib/drm/cef/locales/mr.pak",
				"lib/drm/cef/locales/ms.pak",
				"lib/drm/cef/locales/nb.pak",
				"lib/drm/cef/locales/nl.pak",
				"lib/drm/cef/locales/pl.pak",
				"lib/drm/cef/locales/pt-BR.pak",
				"lib/drm/cef/locales/pt-PT.pak",
				"lib/drm/cef/locales/ro.pak",
				"lib/drm/cef/locales/ru.pak",
				"lib/drm/cef/locales/sk.pak",
				"lib/drm/cef/locales/sl.pak",
				"lib/drm/cef/locales/sr.pak",
				"lib/drm/cef/locales/sv.pak",
				"lib/drm/cef/locales/sw.pak",
				"lib/drm/cef/locales/ta.pak",
				"lib/drm/cef/locales/te.pak",
				"lib/drm/cef/locales/th.pak",
				"lib/drm/cef/locales/tr.pak",
				"lib/drm/cef/locales/uk.pak",
				"lib/drm/cef/locales/ur.pak",
				"lib/drm/cef/locales/vi.pak",
				"lib/drm/cef/locales/zh-CN.pak",
				"lib/drm/cef/locales/zh-TW.pak",
				"lib/drm/cef/resources.pak",
				"lib/drm/cef/snapshot_blob.bin",
				"lib/drm/cef/v8_context_snapshot.bin",
				"lib/drm/cef/vk_swiftshader.dll",
				"lib/drm/cef/vk_swiftshader_icd.json",
				"lib/drm/cef/vulkan-1.dll",
				"lib/drm/cef/swiftshader/libEGL.dll",
				"lib/drm/cef/swiftshader/libGLESv2.dll"
			);
		}
		
		private final List<String> other() {
			return List.of(
				"Web Data",
				"Web Data-journal",
				"resources/drm/md-drm-proxy.p12",
				"resources/drm/md-drm-proxy.pem"
			);
		}
		
		private final List<String> maybeEmptyDirs() {
			return List.of(
				"resources/drm",
				"resources/binary/drm",
				"lib/drm/cef/locales",
				"lib/drm/cef/swiftshader",
				"lib/drm/cef",
				"lib/drm/proxy",
				"lib/drm"
			);
		}
		
		@Override
		public void cleanup() throws Exception {
			List<List<String>> filesToRemove = List.of(
				libraries(), resources(), cef(), other()
			);
			
			for(List<String> files : filesToRemove) {
				for(String path : files) {
					NIO.delete(NIO.localPath(path));
				}
			}
			
			for(String dirPath : maybeEmptyDirs()) {
				Path path = NIO.localPath(dirPath);
				
				if(!NIO.exists(path) || !NIO.isEmptyDirectory(path)) {
					continue;
				}
				
				NIO.deleteDir(path);
			}
		}
		
		@Override
		public boolean canApply(Version current, Version previous) {
			return previous == Version.UNKNOWN
						|| (previous.compareTo(minVersion) >= 0 && previous.compareTo(maxVersion) <= 0);
		}
	}
}