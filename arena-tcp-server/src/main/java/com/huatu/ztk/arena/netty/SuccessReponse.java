package com.huatu.ztk.arena.netty;

import com.huatu.ztk.arena.bean.Player;

import java.util.List;
import java.util.Map;

/**
 * Created by shaojieyue
 * Created time 2016-10-08 14:51
 */
public class SuccessReponse extends Response{
    private Object data;

    /**
     * 登录成功
     * @return
     */
    public static final SuccessReponse loginSuccessResponse(){
        return new SuccessReponse(50000,"身份认证成功");
    }

    public static final SuccessReponse joinGameSuccess(){
        return new SuccessReponse(50001,"加入游戏成功");
    }

    public static final SuccessReponse leaveGameSuccess(){
        return new SuccessReponse(50002,"成功离开游戏");
    }

    /**
     * 存在未完成的游戏
     * @param data
     * @return
     */
    public static final SuccessReponse existGame(Map data){
        return new SuccessReponse(50003,data);
    }

    /**
     * 新添加用户
     * @param players
     * @return
     */
    public static final SuccessReponse newJoinPalyer(List<Player> players){
        return new SuccessReponse(50003,players);
    }

    private SuccessReponse() {
    }

    private SuccessReponse(int code, Object data) {
        this.code = code;
        this.data = data;
    }

    private SuccessReponse(int code,String message) {
        this.code = code;
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
