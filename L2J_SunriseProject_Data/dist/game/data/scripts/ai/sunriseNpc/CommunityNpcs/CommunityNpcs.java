package ai.sunriseNpc.CommunityNpcs;

import l2r.gameserver.model.actor.L2Npc;
import l2r.gameserver.model.actor.instance.L2PcInstance;

import gr.sr.advancedBuffer.SchemeBufferBBSManager;

import ai.npc.AbstractNpcAI;

/**
 * @author L2jSunrise Team
 * @Website www.l2jsunrise.com
 */
public final class CommunityNpcs extends AbstractNpcAI
{
	private final int NPC_BUFFER = 555;
	
	public CommunityNpcs()
	{
		super(CommunityNpcs.class.getSimpleName(), "ai/sunriseNpc");
		
		addFirstTalkId(NPC_BUFFER);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, L2PcInstance player)
	{
		switch (npc.getId())
		{
			case NPC_BUFFER:
				SchemeBufferBBSManager.onBypass(player, "_bbsbufferbypass_redirect main 0 0".substring("_bbsbufferbypass_".length()));
				break;
		}
		return "";
	}
}