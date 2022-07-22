(function() {
	'use strict';

	const _Record = {
		_counter: 0,
		_videoPlayer: null,
		provide(name, data) { window.cefQuery({ request: (name && name.length ? name + ':' : '') + JSON.stringify({ ...data, now: Date.now() }) }); },
		isVideo(node) { return node.tagName === 'VIDEO'; },
		isElement(value) { return value instanceof Element || value instanceof HTMLDocument; },
		vsyncInterval(callable) { const r = window.requestAnimationFrame, c = callable.bind(this), l = (() => { if(c() !== false) r(l); }); r(l); },
		ensureButtonHolder() {
			let holder = document.getElementById('sune-buttons-holder');
			if(holder) return holder; // Already exists
			holder = document.createElement('div');
			holder.id = 'sune-buttons-holder';
			holder.style = 'z-index:2147483647;position:fixed;top:0;left:0;'; // Always visible
			document.body.appendChild(holder);
			return holder;
		},
		playbackData(videoPlayer) {
			const b = videoPlayer.buffered, l = b.length, buffered = l === 0 ? 0 : b.end(l - 1), q = videoPlayer.getVideoPlaybackQuality();
			return { time: videoPlayer.currentTime, frame: q.totalVideoFrames - q.droppedVideoFrames, buffered: buffered };
		},
		inject(videoPlayer) {
			const videoPlayerID = this._counter++;
			const button = document.createElement('button');
			button.type = 'button';
			button.style = 'appearance:none;outline:none;display:block;padding:0;margin:10px;width:20px;height:20px;background:red;'
				+ 'border:none;border-radius:50%;font-size:0;box-shadow:0 0 8px 2px red;';
			button.innerHTML = 'Play';
			button.id = 'sune-button-' + videoPlayerID;
			button.addEventListener('click', (e) => this.play(videoPlayer), true);
			videoPlayer.setAttribute('data-vid', videoPlayerID);
			videoPlayer.style = 'transform:none!important;';
			videoPlayer.addEventListener('playing', (e) => {
				// Do not play the video in non-fullscreen mode
				if(document.fullscreenElement !== videoPlayer) {
					videoPlayer.pause();
					videoPlayer.currentTime = 0.0;
				}
				// Higher-resolution checking of video buffering since events waiting and canplay
				// are NOT reliable.
				if(!videoPlayer.checkInterval) {
					videoPlayer.lastPlaybackData = this.playbackData(videoPlayer);
					videoPlayer.isBuffering  = false;
					videoPlayer.timeUpdated  = false;
					videoPlayer.waitToBuffer = false;
					videoPlayer.checkInterval = (() => {
						const deltaPlaybackTime = videoPlayer.currentTime - videoPlayer.lastPlaybackData.time;
						const playbackData = this.playbackData(videoPlayer);
						if(!videoPlayer.isBuffering && deltaPlaybackTime == 0 && videoPlayer.timeUpdated) { // Video is buffering
							videoPlayer.isBuffering = true;
							videoPlayer.timeUpdated = false;
							this.provide('waiting', playbackData);
						} else if(deltaPlaybackTime > 0) {
							if(videoPlayer.isBuffering) {
								videoPlayer.isBuffering = false;
								this.provide('playing', playbackData);
							} else {
								this.provide('update', playbackData);
								// Pause the video early to wait for it to buffer more.
								if(videoPlayer.waitToBuffer === false && playbackData.buffered - playbackData.time <= 1.0
										// Also check whether the end is far enough so we don't pause the video when it needs to end
										&& videoPlayer.duration - playbackData.time > 1.0) {
									videoPlayer.waitToBuffer = true;
									this.provide('bufferPause', playbackData);
								}
							}
						} else if(videoPlayer.waitToBuffer === true && playbackData.buffered - playbackData.time > 1.0) {
							videoPlayer.waitToBuffer = false;
							this.provide('bufferPlay', playbackData);
						}
						videoPlayer.lastPlaybackData = playbackData;
					});
					this.vsyncInterval(videoPlayer.checkInterval);
				}
			}, true);
			videoPlayer.addEventListener('loadedmetadata', (e) => {
				const holder = this.ensureButtonHolder();
				console.log("hasChildNodes", holder.hasChildNodes());
				if(!holder.hasChildNodes()) {
					holder.appendChild(button); // Add the button
				}
				this.provide('metadata', {
					width:    videoPlayer.videoWidth,
					height:   videoPlayer.videoHeight,
					duration: videoPlayer.duration,
					id:       videoPlayerID,
				});
			}, true);
			videoPlayer.addEventListener('fullscreenchange', (e) => {
				const is_fullscreen = document.fullscreenElement !== null;
				this.provide('fullscreen', { value: is_fullscreen });
				if(is_fullscreen) this._videoPlayer = videoPlayer;
			}, true);
			videoPlayer.addEventListener('timeupdate', (e) => {
				if(!videoPlayer.isBuffering) {
					videoPlayer.timeUpdated = true;
				}
			}, true);
			videoPlayer.addEventListener('canplay', (e) => {
				this.provide('canplay', this.playbackData(videoPlayer));
			}, true);
			videoPlayer.addEventListener('ended', (e) => {
				this.provide('ended', this.playbackData(videoPlayer));
			}, true);
			videoPlayer.addEventListener('pause', (e) => {
				this.provide('waiting', this.playbackData(videoPlayer));
			}, true);
			videoPlayer.addEventListener('playing', (e) => {
				this.provide('playing', this.playbackData(videoPlayer));
			}, true);
		},
		play(videoPlayer) {
			videoPlayer.muted = false;
			videoPlayer.loop = false;
			videoPlayer.autoplay = false;
			videoPlayer.controls = false;
			videoPlayer.poster = '';
			videoPlayer.volume = 1.0;
			videoPlayer.pause();
			videoPlayer.currentTime = 0.0;
			videoPlayer.requestFullscreen();
		},
		activate(selector_or_element) {
			let element = null, is_element = false;

			// Do just a quick arguments check
			if(typeof selector_or_element !== 'string'
					&& !(is_element = this.isElement(selector_or_element)))
				return false;

			// Check for a selector first since there must be some work done
			if(!is_element) {
				const selector = selector_or_element;
				if((element = document.querySelector(selector)) === null) {
					const observe = (() => {
						const observer = new MutationObserver((mutationsList) => {
							mutationsList.forEach((mutation) => {
								let el = null;
								for(const node of mutation.addedNodes) {
									if(node.nodeType !== Node.ELEMENT_NODE)
										continue;

									if(node.matches(selector)) {
										el = node;
										break; // Element found, exit the loop
									} else {
										// Try to serach the element itself for the selector
										if((el = node.querySelector(selector)) !== null) {
											break; // Element found, exit the loop
										}
									}
								}

								if(el !== null) {
									observer.disconnect();
									this.inject(el);
								}
							});
						});

						observer.observe(document.body, { childList: true, subtree: true });
					});

					if(document.body === null) {
						const listener = function() {
							observe();
							document.removeEventListener('DOMContentLoaded', listener, true);
						};
						document.addEventListener('DOMContentLoaded', listener, true);
					} else {
						observe();
					}
				}
			} else {
				element = selector_or_element;
			}

			if(element === null)
				return false; // No element found

			this.inject(element);
			return true; // Element found and activated
		},
		_initialize() {
			// Make sure user cannot leave full-screen mode when the video is playing
			document.addEventListener('dblclick', (e) => {
				if(e.target !== document.fullscreenElement || !this._videoPlayer)
					return; // Not the video player or it is not playing yet
				e.preventDefault();
				e.stopPropagation();

				let is_fullscreen = document.fullscreenElement !== null;
				if(is_fullscreen) {
					const intr = (() => {
						is_fullscreen = document.fullscreenElement !== null;
						if(!is_fullscreen) {
							this._videoPlayer.requestFullscreen();
							clearInterval(intr);
						}
					});
					this.vsyncInterval(intr);
				} else {
					this._videoPlayer.requestFullscreen();
				}
			}, true);

			// Disable click on a video to play/pause it
			document.addEventListener('click', (e) => {
				if(e.target !== document.fullscreenElement
						|| !this._videoPlayer
						|| this._videoPlayer !== document.fullscreenElement)
					return; // Not the video player or it is not playing yet
				e.preventDefault();
				e.stopPropagation();
			}, true);

			// Disable context menu for the whole document
			document.addEventListener('contextmenu', (e) => {
				e.preventDefault();
				e.stopPropagation();
			}, true);
		},
		// Utility methods
		fnc(f) { return f.bind(this); }, // Bind 'this' to the current object
		obj(o) { return Object.fromEntries(Object.entries(o).map(([k, v], i) => [ k, v.bind(this) ])); }, // Bind 'this' to the current object
		neg(f) { return function() { return !f(...arguments); }; },
	};

	_Record._initialize();

	window.MediaDownloader = window.MediaDownloader || { DRM: {} };
	window.MediaDownloader.DRM.Record = _Record;
})();