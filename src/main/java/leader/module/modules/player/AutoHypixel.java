package leader.module.modules.player;

import leader.event.EventTarget;
import leader.event.types.EventType;
import leader.event.types.Priority;
import leader.events.PacketEvent;
import leader.module.Module;
import leader.module.modules.render.notification.NoticeMode;
import leader.module.modules.render.notification.Notification;
import leader.util.ChatUtil;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;

import java.util.Iterator;

import static leader.config.Config.mc;

public class AutoHypixel extends Module {
    public AutoHypixel() {
        super("AutoHypixel", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (!isEnabled() || event.getType() == EventType.SEND) {
            return;
        }
        if (!(event.getPacket() instanceof S02PacketChat)) {
            return;
        }

        S02PacketChat packet = (S02PacketChat) event.getPacket();
        if (packet.isChat()) {
            return;
        }

        if (packet.getChatComponent().getFormattedText().contains("play again?")) {
            Iterator<IChatComponent> iterator = packet.getChatComponent().getSiblings().iterator();
            while (iterator.hasNext()) {
                for (String command : iterator.next().toString().split("'")) {
                    if (command.startsWith("/play") && !command.contains(".")) {
                        if (mc.thePlayer != null) {
                            ChatUtil.sendMessage(command);
                        }
                        Notification.addNotification("AutoPlay Running...", NoticeMode.Info);
                        break;
                    }
                }
            }
        }
    }
}
