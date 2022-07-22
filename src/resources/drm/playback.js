(function() {
	'use strict';

	const _Playback = {
		// Utility methods
		_eq: function(a, b) { return Math.abs(a - b) <= 0.000001; },
		_videoPlayer: function(video_id) { return document.querySelector('video[data-vid="' + video_id + '"]'); },
		_isPlaying: function(video) { return video.readyState > 2 && !video.paused && !video.ended && !video.seeking; },
		// Standard methods
		play: function(video_id) {

		},
		pause: function(video_id) {

		},
		time: function(video_id, time, keepPaused, ret) {
			const video = this._videoPlayer(video_id);

			console.log("PLAYBACK CONTROLLER", "video", video);

			if(this._eq(video.currentTime, time)) {
			    ret(0, {});
			} else {
				const was_playing = !keepPaused && this._isPlaying(video);
				
				console.log("PLAYBACK CONTROLLER", "was_playing", was_playing);

				// Pause method can be called only when a user has done some interaction (click, etc.)
				MediaDownloader.DRM.Helper.doUserInteraction(() => {
					// First, pause the video so that the seeking can be done safely
					video.pause();

					console.log("PLAYBACK CONTROLLER", "pause");

				    const seeked = ((e) => {
				    	// Check whether the desired time has been seeked
				        if(!this._eq(video.currentTime, time))
				        	return;

				        // If so, clean up
			            video.removeEventListener('seeked', seeked, true);

			            // We must play the video again, but only if it was already playing
			            if(was_playing) {
			            	// Play method can be called only when a user has done some interaction (click, etc.)
			            	MediaDownloader.DRM.Helper.doUserInteraction(() => {

			            		console.log("PLAYBACK CONTROLLER", "play");

			            		// Resolve after the video is playing again
			            		video.play().then(() => ret(0, {}));
			            	});
			            } else {
			            	// Video was not playing before, nothing to do, just resolve
			            	ret(0, {});
			            }
				    });
				    video.addEventListener('seeked', seeked, true);

				    // Seek to the desired time
				    video.currentTime = time;
				});
			}
		},
		muted: function(video_id, muted, ret) {
			const video = this._videoPlayer(video_id);
			// Muted/Unmuted state can be set only when a user has done some interaction (click, etc.)
			MediaDownloader.DRM.Helper.doUserInteraction(() => {
				video.muted = muted;
				ret(0, {});
			});
		},
		volume: function(video_id, volume, ret) {
			const video = this._videoPlayer(video_id);
			// Volume can be set only when a user has done some interaction (click, etc.)
			MediaDownloader.DRM.Helper.doUserInteraction(() => {
				video.volume = volume;
				ret(0, {});
			});
		},
	};

	window.MediaDownloader = window.MediaDownloader || { DRM: {} };
	window.MediaDownloader.DRM.Playback = _Playback;
})();
