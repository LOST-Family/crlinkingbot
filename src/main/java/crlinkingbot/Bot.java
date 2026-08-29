package crlinkingbot;

import java.io.IOException;

import javax.security.auth.login.LoginException;

import crlinkingbot.api.QueueAPIServer;
import crlinkingbot.listeners.LinkCommand;
import crlinkingbot.listeners.LinkDirectlyCommand;
import crlinkingbot.listeners.PhishTrap;
import crlinkingbot.listeners.RemoveCommand;
import crlinkingbot.listeners.RestartCommand;
import crlinkingbot.queue.RequestQueue;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Main bot class that initializes the Discord bot and stores configuration.
 */
public class Bot {
	// Configuration from environment variables
	private static String genaiApiKey;
	private static String lostCRManagerUrl;
	private static String lostCRManagerSecret;
	private static String lostCRManagerManageUrl;
	private static String lostCRManagerManageToken;

	// Queue system components
	private static RequestQueue requestQueue;
	private static QueueAPIServer apiServer;

	public static void main(String[] args) {
		System.out.println("Starting CR Linking Bot...");

		// Load environment variables
		if (!loadEnvironmentVariables()) {
			System.out.println("Failed to load environment variables. Exiting.");
			System.exit(1);
		}

		System.out.println("Configuration loaded successfully");

		// Initialize request queue before JDA
		System.out.println("Initializing request queue...");
		requestQueue = new RequestQueue();

		// Initialize JDA
		String botToken = System.getenv("CRLINKING_BOT_TOKEN");
		try {
			JDA jda = JDABuilder.createDefault(botToken)
					.enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT,
							GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_MEMBERS)
					.addEventListeners(new LinkCommand(requestQueue), new LinkDirectlyCommand(), new RemoveCommand(),
							new RestartCommand(), new PhishTrap()).build();

			jda.awaitReady();

			// Register slash commands to specific guild
			String guildId = "1108449987827876022";
			var guild = jda.getGuildById(guildId);
			if (guild != null) {
				System.out.println("Registering slash commands for guild: " + guild.getName() + " (" + guildId + ")");
				guild.updateCommands().addCommands(
						Commands.slash("link", "Link einen Clash Royale Account über eine Nachricht mit Screenshots")
								.addOption(OptionType.STRING, "message_link",
										"Link zur Nachricht mit den CR Screenshots",
										true)
								.addOptions(
										new OptionData(OptionType.STRING, "noping", "Ping abschalten").addChoice("true",
												"true")),
						Commands.slash("linkdirectly", "Direktes Verlinken eines Discord-Users zu einem Player-Tag")
								.addOption(OptionType.USER, "user", "Der zu verlinkende Discord-User", true)
								.addOption(OptionType.STRING, "tag", "Der Clash Royale Player-Tag (z.B. #ABC123)", true)
								.addOptions(
										new OptionData(OptionType.STRING, "noping", "Ping abschalten").addChoice("true",
												"true")),
						Commands.slash("remove", "Entfernt die Clan-Zugehörigkeit und Verlinkung eines Spielers")
								.addOptions(
										new OptionData(OptionType.STRING, "player",
												"Der Spieler, welcher entfernt werden soll", true)
												.setAutoComplete(true)),
						Commands.slash("restartcrmanager",
								"Startet den CR Manager Bot neu (Admin oder Restart-Rolle)."))
						.queue();
				System.out.println("Slash commands registered successfully for guild " + guildId);
			} else {
				System.out.println(
						"WARNING: Guild " + guildId + " not found. Could not register guild-specific commands.");
			}

			System.out.println("CR Linking Bot is ready! Logged in as: " + jda.getSelfUser().getAsTag());

			// Initialize and start queue API server
			System.out.println("Starting queue API server...");
			apiServer = new QueueAPIServer(requestQueue, jda);
			apiServer.start();

			// Add shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.out.println("Shutting down bot...");
				if (apiServer != null) {
					apiServer.shutdown();
				}
			}));

		} catch (final IOException | InterruptedException | LoginException e) {
			System.out.println("Failed to initialize JDA: " + e);
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}

	/**
	 * Load required environment variables
	 */
	private static boolean loadEnvironmentVariables() {
		String botToken = System.getenv("CRLINKING_BOT_TOKEN");
		genaiApiKey = System.getenv("GOOGLE_GENAI_API_KEY");
		lostCRManagerUrl = System.getenv("LOSTCRMANAGER_API_URL");
		lostCRManagerSecret = System.getenv("LOSTCRMANAGER_API_SECRET");

		if (botToken == null || botToken.isEmpty()) {
			System.out.println("CRLINKING_BOT_TOKEN environment variable is not set");
			return false;
		}

		if (genaiApiKey == null || genaiApiKey.isEmpty()) {
			System.out.println("GOOGLE_GENAI_API_KEY environment variable is not set");
			return false;
		}

		if (lostCRManagerUrl == null || lostCRManagerUrl.isEmpty()) {
			System.out.println("LOSTCRMANAGER_API_URL environment variable is not set");
			return false;
		}

		if (lostCRManagerSecret == null || lostCRManagerSecret.isEmpty()) {
			System.out.println("LOSTCRMANAGER_API_SECRET environment variable is not set");
			return false;
		}

		// Optional: management API of lostcrmanager (RestApiServer, default port
		// 8060) for /restartcrmanager. The bot still works without these.
		lostCRManagerManageUrl = System.getenv("LOSTCRMANAGER_MANAGE_URL");
		lostCRManagerManageToken = System.getenv("LOSTCRMANAGER_MANAGE_TOKEN");

		if (lostCRManagerManageUrl == null || lostCRManagerManageUrl.isEmpty()) {
			System.out.println(
					"WARNING: LOSTCRMANAGER_MANAGE_URL is not set - /restartcrmanager will not work until it is configured");
		}
		if (lostCRManagerManageToken == null || lostCRManagerManageToken.isEmpty()) {
			System.out.println(
					"WARNING: LOSTCRMANAGER_MANAGE_TOKEN is not set - restart requests will be sent without authentication");
		}

		System.out.println("Environment variables loaded successfully");
		return true;
	}

	/**
	 * Get the Gemini API key
	 */
	public static String getGenaiApiKey() {
		return genaiApiKey;
	}

	/**
	 * Get the LostCRManager API URL
	 */
	public static String getLostCRManagerUrl() {
		return lostCRManagerUrl;
	}

	/**
	 * Get the LostCRManager API secret
	 */
	public static String getLostCRManagerSecret() {
		return lostCRManagerSecret;
	}

	/**
	 * Get the LostCRManager management API URL (RestApiServer, e.g.
	 * http://localhost:8060)
	 */
	public static String getLostCRManagerManageUrl() {
		return lostCRManagerManageUrl;
	}

	/**
	 * Get the LostCRManager management API token (REST_API_TOKEN of lostcrmanager)
	 */
	public static String getLostCRManagerManageToken() {
		return lostCRManagerManageToken;
	}

	/**
	 * Get the request queue
	 */
	public static RequestQueue getRequestQueue() {
		return requestQueue;
	}
}
