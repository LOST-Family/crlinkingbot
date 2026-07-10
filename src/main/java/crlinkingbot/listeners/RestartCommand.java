package crlinkingbot.listeners;

import org.json.JSONObject;

import crlinkingbot.Bot;
import crlinkingbot.services.LostCRManagerClient;
import crlinkingbot.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Command listener for restarting the lostcrmanager bot via its management
 * API. Permission (admin or restart role) is enforced server-side by
 * lostcrmanager based on the invoking user's Discord ID.
 */
public class RestartCommand extends ListenerAdapter {

    @SuppressWarnings("null")
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("restartcrmanager")) {
            return;
        }

        event.deferReply().queue();

        new Thread(() -> {
            String title = "CR Manager Neustart";

            String manageUrl = Bot.getLostCRManagerManageUrl();
            if (manageUrl == null || manageUrl.isEmpty()) {
                event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title,
                        "Die Management-API ist nicht konfiguriert (LOSTCRMANAGER_MANAGE_URL fehlt)."))
                        .queue();
                return;
            }

            JSONObject result = LostCRManagerClient.restartCRManager(event.getUser().getId());

            if (result.getBoolean("success")) {
                event.getHook().editOriginalEmbeds(MessageUtil.createSuccessEmbed(title,
                        "Der CR Manager wird neugestartet. Er sollte in wenigen Sekunden wieder erreichbar sein."))
                        .queue();
                System.out.println("CR Manager restart initiated by " + event.getUser().getAsTag()
                        + " (" + event.getUser().getId() + ")");
                return;
            }

            String errorMsg;
            if (result.optBoolean("connectionError", false)) {
                errorMsg = "Der CR Manager ist nicht erreichbar - die Restart-API antwortet nicht.\n\n"
                        + "Wenn der Prozess komplett hängt oder beendet ist, muss er manuell neugestartet werden.\n\n"
                        + "**Fehler:** " + result.optString("error", "unbekannt");
            } else if (result.optInt("statusCode", 0) == 403) {
                JSONObject data = result.optJSONObject("data");
                String serverError = data != null ? data.optString("error", "") : "";
                errorMsg = "Du hast keine Berechtigung, den CR Manager neuzustarten. "
                        + "Nur Admins oder Mitglieder mit der Restart-Rolle dürfen das.";
                if (!serverError.isEmpty()) {
                    errorMsg += "\n\n**Server-Antwort:** " + serverError;
                }
            } else {
                errorMsg = "Der Neustart ist fehlgeschlagen.";
                JSONObject data = result.optJSONObject("data");
                if (data != null && data.has("error")) {
                    errorMsg += "\n\n**Fehler:** " + data.getString("error");
                } else if (result.has("error")) {
                    errorMsg += "\n\n**Fehler:** " + result.getString("error");
                }
            }
            event.getHook().editOriginalEmbeds(MessageUtil.createErrorEmbed(title, errorMsg)).queue();

        }, "RestartCommand-" + event.getUser().getId() + "-" + System.currentTimeMillis()).start();
    }
}
