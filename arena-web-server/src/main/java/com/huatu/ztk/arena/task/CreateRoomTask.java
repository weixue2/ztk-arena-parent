package com.huatu.ztk.arena.task;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.huatu.ztk.arena.bean.ArenaConfig;
import com.huatu.ztk.arena.bean.ArenaRoom;
import com.huatu.ztk.arena.bean.ArenaRoomStatus;
import com.huatu.ztk.arena.common.Actions;
import com.huatu.ztk.arena.common.RedisArenaKeys;
import com.huatu.ztk.arena.dubbo.ArenaDubboService;
import com.huatu.ztk.arena.service.ArenaRoomService;
import com.huatu.ztk.commons.ModuleConstants;
import com.huatu.ztk.paper.api.PracticeCardDubboService;
import com.huatu.ztk.paper.bean.PracticeCard;
import com.huatu.ztk.paper.common.AnswerCardType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 *
 * 创建房间task
 * Created by shaojieyue
 * Created time 2016-10-08 21:36
 */

@Component
@Scope("singleton")
public class CreateRoomTask {
    private static final Logger logger = LoggerFactory.getLogger(CreateRoomTask.class);
    //最小玩家人数
    public static final int MIN_COUNT_PALYER_OF_ROOM = 2;
    /**
     * 任务是否运行的表示
     */
    private volatile boolean running = true;

    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    private ArenaRoomService arenaRoomService;

    @Autowired
    private PracticeCardDubboService practiceCardDubboService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ArenaDubboService arenaDubboService;

    @PostConstruct
    public void init() {
        for (ArenaConfig.Module module : ArenaConfig.getConfig().getModules()) {
            startWork(module.getId());
        }
        //添加停止任务线程
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                running = false;//停止任务

                //遍历释放锁
                for (ArenaConfig.Module module : ArenaConfig.getConfig().getModules()) {

                    //释放锁
                    final String workLockKey = RedisArenaKeys.getWorkLockKey(module.getId());

                    //获取锁的值
                    final String value = redisTemplate.opsForValue().get(workLockKey);
                    if (getLockValue().equals(value)) {//如果是自己抢到的锁,才删除
                        redisTemplate.delete(workLockKey);
                        logger.info("release lock,moduleId={}",module.getId());
                    }
                }

            }
        }));
    }

    private void startWork(Integer moduleId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //锁是否被抢占
                boolean locked = true;
                while (running && locked){
                    try {
                        //通过setnx 来实现简单分布式锁
                        locked = !redisTemplate.opsForValue().setIfAbsent(RedisArenaKeys.getWorkLockKey(moduleId), getLockValue()).booleanValue();
                        if (locked) {
                            //锁被抢占,则sleep 一段时间
                            TimeUnit.SECONDS.sleep(3);
                        }
                    } catch (Exception e) {
                        logger.error("wait lock ex",e);
                    }
                }
                logger.info("server_ip={},moduleId={} get the lock,and run task.",System.getProperty("server_ip"),moduleId);
                //创建房间
                ArenaRoom arenaRoom = null;
                while (running){
                    try {
                        if (arenaRoom == null) {
                            //创建房间
                            arenaRoom = arenaRoomService.create(moduleId);
                        }
                        final long arenaRoomId = arenaRoom.getId();
                        final String roomUsersKey = RedisArenaKeys.getRoomUsersKey(arenaRoomId);
                        final String arenaUsersKey = RedisArenaKeys.getArenaUsersKey(moduleId);
                        final SetOperations<String, String> setOperations = redisTemplate.opsForSet();
                        long start = Long.MAX_VALUE;//开始时间,默认不过期
                        //拥有足够人数和等待超时,则跳出循环
                        while (setOperations.size(roomUsersKey) < ArenaConfig.getConfig().getRoomCapacity() && System.currentTimeMillis()-start < ArenaConfig.getConfig().getWaitTime()*1000){
                            final String userId = setOperations.pop(arenaUsersKey);
                            if (StringUtils.isBlank(userId)) {
                                Thread.sleep(1000);//没有玩家则休眠一段时间
                                continue;
                            }
                            //把用户加入游戏
                            setOperations.add(roomUsersKey,userId);

                            final String userRoomKey = RedisArenaKeys.getUserRoomKey(Long.valueOf(userId));
                            //设置用户正在进入的房间
                            redisTemplate.opsForValue().set(userRoomKey,arenaRoomId+"");
                            logger.info("add userId={} to roomId={}",userId,arenaRoomId);
                            Map data = Maps.newHashMap();
                            data.put("action", Actions.USER_JOIN_NEW_ARENA);
                            //发送加入游戏通知
                            data.put("uid",Long.valueOf(userId));
                            data.put("roomId", arenaRoomId);
                            //通过mq发送新人进入通知
                            rabbitTemplate.convertAndSend("game_notify_exchange","",data);

                            //超时时间从第一个加入房间用户开始算起
                            if (setOperations.size(roomUsersKey) == 1) {
                                start = System.currentTimeMillis();//开始超时倒计时
                            }
                        }

                        final Long finalSize = setOperations.size(roomUsersKey);
                        if (finalSize < MIN_COUNT_PALYER_OF_ROOM) {//没有达到最小玩家人数
                            final Set<String> users = setOperations.members(roomUsersKey);
                            logger.info("playerIds wait time out. users={}",users);
                            redisTemplate.delete(roomUsersKey);//清除用户数据
                            for (String user : users) {
                                final String userRoomKey = RedisArenaKeys.getUserRoomKey(Long.valueOf(user));
                                //清除用户占用的房间
                                redisTemplate.delete(userRoomKey);
                            }
                            continue;
                        }

                        long[] users = setOperations.members(roomUsersKey).stream().mapToLong(userId->Long.valueOf(userId)).toArray();
                        //设置有效期,让其自动回收
                        redisTemplate.expire(roomUsersKey,1,TimeUnit.HOURS);
                        List<Long> practiceIds = Lists.newArrayList();
                        for (Long uid : users) {//为用户创建练习
                            final PracticeCard practiceCard = practiceCardDubboService.create(arenaRoom.getPracticePaper(), -1, AnswerCardType.ARENA_PAPER, uid);
                            practiceIds.add(practiceCard.getId());
                        }

                        Update update = Update.update("playerIds",users)
                                    .set("practices",practiceIds)
                                    .set("createTime",System.currentTimeMillis())//重新设置开始时间,倒计时时间以此为起始时间
                                    .set("status", ArenaRoomStatus.RUNNING);
                        //更新房间数据
                        arenaDubboService.updateById(arenaRoomId,update);

                        arenaRoom = null;//设置为null,表示该房间已经被占用
                        Map data = Maps.newHashMap();
                        data.put("roomId", arenaRoomId);
                        data.put("action", Actions.SYSTEM_START_GAME);
                        data.put("uids",users);
                        data.put("practiceIds",practiceIds);//用户对应的练习列表
                        //通过mq发送游戏就绪通知
                        rabbitTemplate.convertAndSend("game_notify_exchange","",data);
                        logger.info("roomId={},users={} start game.",arenaRoomId,users);
                    }catch (Exception e){
                        logger.error("ex",e);
                    }
                }
                logger.info("moduleId={} work stoped",moduleId);
            }
        }).start();
        logger.info("moduleId={} work started.",moduleId);
    }

    private String getLockValue() {
        return System.getProperty("server_name")+System.getProperty("server_ip");
    }
}
