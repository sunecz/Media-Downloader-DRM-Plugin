# Media Downloader - DRM Plugin
DRM plugin for Media Downloader.

## Legal disclaimer
This plugin is for educational purposes only. Downloading copyrighted materials from streaming services may violate their Terms of Service. **Use at your own risk.**

## How to build
Building can be done either manually, or using [Apache's Ant](https://ant.apache.org/).
The Ant script can be run either directly on the host machine or in the prepared Docker image.

To run the following commands on Windows, use PowerShell.

The following subsections assume you have already built the [Media Downloader](https://github.com/sunecz/Media-Downloader#how-to-build) project first and you clone this repository to a directory next to the `Media-Downloader` directory.

### Clone the repository
```shell
git clone https://github.com/sunecz/Media-Downloader-DRM-Plugin.git Media-Downloader-DRM
cd Media-Downloader-DRM/
```

### Build using the Docker image
Finally, build the JAR:
```shell
docker run --rm -v "$(pwd):/workdir" -v "$(pwd)/../Media-Downloader:/Media-Downloader" --name md-build -it md:build ant -D"path.javafx"="/Media-Downloader/docker/openjfx"
```

# Related repositories
- Application: https://github.com/sunecz/Media-Downloader
- Default plugins: https://github.com/sunecz/Media-Downloader-Default-Plugins
- Launcher: https://github.com/sunecz/Media-Downloader-Launcher
