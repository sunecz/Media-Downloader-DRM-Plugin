(function() {
	'use strict';

	const _Helper = {
		isElement(value) { return value instanceof Element || value instanceof HTMLDocument; },
		// Source: https://stackoverflow.com/a/53872705
		// Code has been modified.
		currentFramePosition: function() {
			const position = { x: 0.0, y: 0.0 };
			for(let current = window, parent; current !== window.top;) {
				parent = current.parent;
				for(let i = 0, len = parent.frames.length; i < len; ++i) {
					if(parent.frames[i] === current) {
						try {
							for(const frame of parent.document.getElementsByTagName('iframe')) {
								if(frame.contentWindow === current) {
									const rect = frame.getBoundingClientRect();
									position.x += rect.x;
									position.y += rect.y;
									break;
								}
							}
							current = parent;
							break;
						} catch(error) {
							// Unable to obtain the parent document, probably because of cross-origin blocking.
							// Just return the position so far.
							return position;
						}
					}
				}
			}
			return position;
		},
		includeStyle: function(content) {
			const style = document.createElement('style');
			style.textContent = content;
			document.head.appendChild(style);
		},
		hideVideoElementStyle: function() {
			this.includeStyle('video::-webkit-media-controls{display:none!important;-webkit-appearance:none!important;}');
		},
		querySelector: function(selector) {
			return new Promise((resolve, reject) => {
				let element = null;
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
									resolve(el);
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
				} else resolve(element);
			});
		},
		bbox: function(selector_or_element) {
			const bbox = { x: NaN, y: NaN, width: NaN, height: NaN };
			let is_element = false;

			// Do just a quick arguments check
			if(typeof selector_or_element !== 'string'
					&& !(is_element = this.isElement(selector_or_element)))
				return bbox;

			let element = selector_or_element;
			if(!is_element) {
				element = document.querySelector(selector_or_element);
			}

			if(element === null)
				return bbox;

			const rect = element.getBoundingClientRect();
		    const fpos = this.currentFramePosition();
		    bbox.x = rect.x + fpos.x;
		    bbox.y = rect.y + fpos.y;
		    bbox.width = rect.width;
		    bbox.height = rect.height;

			return bbox;
		},
		click: function(selector, callback) {
			this.querySelector(selector).then((button) => {
				let clicked = false, sent = false;
				const click = ((e) => { clicked = true; });
				button.addEventListener('click', click, false);

				const intr = setInterval(() => {
				    if(clicked) {
				    	clearInterval(intr);
				    	button.removeEventListener('click', click, false);
				    } else if(!sent) {
			    		callback(this.bbox(button));
			    		sent = true;
				    }
				}, 100);
			});
		},
		doRequest: function(request_name, callback) {
			return new Promise((_rs,_rj) => {
				// Pass the 'return' function as an argument
				callback(function(i, d) {
					window.cefQuery({ request: request_name + '.' + i + ':' + JSON.stringify({ 'data': d }) });
					_rs(0);
				});
			});
		},
		doUserInteraction: function(callback) {
			// Register listener, so that the click interaction can be caught
			const click = ((e) => {
				e.preventDefault();
				e.stopPropagation();
				document.removeEventListener('click', click, true);
				callback();
			});
			document.addEventListener('click', click, true);

			// Request the click interaction from the application
			this.doRequest('doUserInteraction', (ret) => ret(0, {}));
		},
	};

	window.MediaDownloader = window.MediaDownloader || { DRM: {} };
	window.MediaDownloader.DRM.Helper = _Helper;
})();
