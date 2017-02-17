package me.philippheuer.twitch4j.auth;

import lombok.Getter;
import lombok.Setter;
import me.philippheuer.twitch4j.auth.model.OAuthCredential;
import ratpack.server.RatpackServer;
import ratpack.server.Stopper;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

@Getter
@Setter
public class OAuthHandler {
	/**
	 * Holds the Credential Manager
	 */
	private CredentialManager credentialManager;

	/**
	 * Holds the Ratpack Server Instance
	 */
	private RatpackServer ratpackServer;

	/**
	 * Port for local webserver
	 * Will be used to recieve oauth redirect.
	 */
	private Integer localPort = 7090;

	/**
	 * Constructor
	 */
	public OAuthHandler(CredentialManager credentialManager) {
		setCredentialManager(credentialManager);
		initalize();
	}

	/**
	 * Get server base address for redirect urls
	 */
	public String getServerBaseAddress() {
		return String.format("http://127.0.0.1:%s", getLocalPort().toString());
	}

	/**
	 * Start Listener
	 */
	public void initalize() {
		try {
			ratpackServer = RatpackServer.of(s -> s
					.serverConfig(c -> c
							.port(getLocalPort())
					)
					.handlers(c -> c
							.get(ctx -> ctx.render("Local OAuth Listener ..."))
							// Twitch
							.get(OAuthTwitch.REDIRECT_KEY,
									ctx -> {
										// Parse Parameters
										String responseCode = ctx.getRequest().getQueryParams().get("code");
										String responseScope = ctx.getRequest().getQueryParams().get("scope");
										String responseState = ctx.getRequest().getQueryParams().get("state");

										// Handle Response
										OAuthCredential credential = getCredentialManager().getOAuthTwitch().handleAuthenticationCodeResponseTwitch(responseCode);

										// Valid?
										if (credential != null) {
											// Add requested Scopes to credential (separated by space when more than one is requested)
											if (responseScope.contains(" ")) {
												credential.getOAuthScopes().addAll(Arrays.asList(responseScope.split("\\s")));
											} else {
												credential.getOAuthScopes().add(responseScope);
											}

											// Check for custom key, to store in credential manager
											if (responseState.length() > 0 && !responseState.equals("CHANNEL")) {
												// Custom Key
												getCredentialManager().addTwitchCredential(responseState, credential);
											} else {
												// Channel Credentials
												getCredentialManager().addTwitchCredential(credential.getUserId().toString(), credential);
											}

											ctx.render("Welcome " + credential.getDisplayName() + "!");
										} else {
											ctx.render("Authentication failed!");
										}

										// Response received, close listener
										ctx.onClose(outcome -> {
											new Thread(ctx.get(Stopper.class)::stop).start();
										});
									}
							)
							// Streamlabs
							.get(OAuthStreamlabs.REDIRECT_KEY,
									ctx -> {
										// Parse Parameters
										String responseCode = ctx.getRequest().getQueryParams().get("code");
										String responseState = ctx.getRequest().getQueryParams().get("state");

										// Handle Response
										OAuthCredential credential = credentialManager.getOAuthStreamlabs().handleAuthenticationCodeResponseStreamlabs(responseCode);

										// Valid?
										if (credential != null) {
											// Check for custom key, to store in credential manager
											if (responseState.length() > 0 && !responseState.equals("CHANNEL")) {
												// Custom Key
												getCredentialManager().addStreamlabsCredential(responseState, credential);
											} else {
												// Channel Credentials
												getCredentialManager().addStreamlabsCredential(credential.getUserId().toString(), credential);
											}

											ctx.render("Welcome " + credential.getDisplayName() + "!");
										} else {
											ctx.render("Authentication failed!");
										}

										// Response received, close listener
										ctx.onClose(outcome -> {
											new Thread(ctx.get(Stopper.class)::stop).start();
										});
									}
							)
					)
			);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void onRequestPermission() {
		// User Requested permission, start the listener for the next 5 minutes
		if (!ratpackServer.isRunning()) {
			try {
				// RatpackServer: Start
				ratpackServer.start();

				final Timer timer = new Timer();
				timer.schedule(new TimerTask() {
					public void run() {
						// RatpackServer: Stop
						try {
							if(ratpackServer.isRunning()) {
								ratpackServer.stop();
							}
						} catch (Exception ex) {
							ex.printStackTrace();
						}

					}
				}, 360 * 1000);

			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}