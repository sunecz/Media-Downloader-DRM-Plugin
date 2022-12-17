(function() {
	'use strict';

	const _Helper = {
		isElement(value) { return value instanceof Element || value instanceof HTMLDocument; },
		_iframePosition: function() {
			return new Promise((resolve, reject) => {
				const listener = ((e) => {
					if(e.source !== window.parent) {
						return;
					}

					const { 'md': md, 'type': type, 'name': name, 'data': position } = e.data;

					if(md !== true || type !== 'response' || name !== 'iframe-rect') {
						return;
					}

					window.removeEventListener('message', listener, true);
					resolve(position);
				});

				const request = { md: true, type: 'request', name: 'iframe-rect' };

				window.addEventListener('message', listener, true);
				window.parent.postMessage(request, '*');
			});
		},
		_processInterframeRequest: function(source, name) {
			// Handle the request by its name
			switch(name) {
				case 'iframe-rect':
					const frames = window.frames;

					for(let i = 0, l = frames.length; i < l; ++i) {
						const frame = frames[i];

						if(frame !== source) {
							continue;
						}

						for(const iframe of document.getElementsByTagName('iframe')) {
							if(iframe.contentWindow !== frame) {
								continue;
							}

							return iframe.getBoundingClientRect();
						}
					}

					return { x: NaN, y: NaN };
				default:
					// Unsupported, ignore
					return null;
			}
		},
		_processInterframeCommunication: function(source, data) {
			// Process only objects
			if(typeof data !== 'object'
					|| data === null
					|| Array.isArray(data)) {
				return null;
			}

			const { 'md': md, 'type': type, 'name': name } = data;

			// Not from Media Downloader, ignore
			if(md !== true) {
				return null;
			}

			switch(type) {
				case 'request':
					const response = this._processInterframeRequest(source, name);
					return { md: true, type: 'response', name: name, data: response };
				default:
					// Unsupported, ignore
					return null;
			}
		},
		_currentFramePosition: function() {
			return window === window.top
						? new Promise((r) => r({ x: 0.0, y: 0.0 }))
						: this._iframePosition();
		},
		enableInterframeCommunication: function() {
			window.addEventListener('message', (e) => {
				const response = this._processInterframeCommunication(e.source, e.data);

				if(response !== null) {
					e.source.postMessage(response, e.origin);
				}
			}, true);
		},
		includeStyle: function(content) {
			const style = document.createElement('style');
			style.textContent = content;

			if(document.head !== null) {
				document.head.appendChild(style);
			} else {
				const on_load = ((e) => {
					document.head.appendChild(style);
					document.removeEventListener('DOMContentLoaded', on_load, true);
				});
				document.addEventListener('DOMContentLoaded', on_load, true);
			}
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
		bbox: async function(selector_or_element) {
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
			const fpos = await this._currentFramePosition();
			bbox.x = rect.x + fpos.x;
			bbox.y = rect.y + fpos.y;
			bbox.width = rect.width;
			bbox.height = rect.height;

			return bbox;
		},
		click: async function(selector, callback) {
			this.querySelector(selector).then((button) => {
				let clicked = false, sent = false;
				const click = ((e) => { clicked = true; });
				button.addEventListener('click', click, false);

				const intr = setInterval(async () => {
					if(clicked) {
						clearInterval(intr);
						button.removeEventListener('click', click, false);
					} else if(!sent) {
						callback(await this.bbox(button));
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
