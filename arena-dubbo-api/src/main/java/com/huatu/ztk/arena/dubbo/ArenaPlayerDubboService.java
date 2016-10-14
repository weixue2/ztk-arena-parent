package com.huatu.ztk.arena.dubbo;

import com.huatu.ztk.arena.bean.ArenaUserSummary;
import com.huatu.ztk.arena.bean.Player;

/**
 * 玩家dubbo服务
 * Created by shaojieyue
 * Created time 2016-10-13 14:46
 */
public interface ArenaPlayerDubboService {
    /**
     * 根据uid查询玩家
     * @param uid
     * @return
     */
    public Player findById(long uid);

    /**
     * 根据uid查询用户竞技场统计(胜负场次)
     * @param uid
     * @return
     */
    public ArenaUserSummary findSummaryById(long uid);
}
