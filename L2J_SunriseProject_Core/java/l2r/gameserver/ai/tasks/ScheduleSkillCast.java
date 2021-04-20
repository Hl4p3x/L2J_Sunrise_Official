package l2r.gameserver.ai.tasks;

import l2r.gameserver.enums.CtrlIntention;
import l2r.gameserver.model.L2Object;
import l2r.gameserver.model.actor.L2Character;
import l2r.gameserver.model.skills.L2Skill;

/**
 * @author vGodFather
 */
public class ScheduleSkillCast implements Runnable
{
	private final L2Character _activeChar;
	private final L2Object _target;
	private final L2Skill _skill;
	
	public ScheduleSkillCast(L2Character actor, L2Skill skill, L2Object target)
	{
		_activeChar = actor;
		_target = target;
		_skill = skill;
	}
	
	@Override
	public void run()
	{
		if (_activeChar.isAttackingNow())
		{
			_activeChar.abortAttack();
		}
		
		_activeChar.getAI().setIntention(CtrlIntention.AI_INTENTION_CAST, _skill, _target);
	}
}