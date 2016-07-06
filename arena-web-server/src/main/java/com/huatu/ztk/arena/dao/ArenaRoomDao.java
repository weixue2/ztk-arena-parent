package com.huatu.ztk.arena.dao;

import com.huatu.ztk.arena.bean.ArenaRoom;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by shaojieyue
 * Created time 2016-07-05 15:21
 */

@Repository
public class ArenaRoomDao {
    private static final Logger logger = LoggerFactory.getLogger(ArenaRoomDao.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    public ArenaRoom findById(long roomId) {
        return mongoTemplate.findById(roomId,ArenaRoom.class);
    }

    public void save(ArenaRoom arenaRoom) {
        mongoTemplate.save(arenaRoom);
    }

    public void insert(ArenaRoom arenaRoom) {
        mongoTemplate.insert(arenaRoom);
    }

    /**
     * 通过id批量查询房间信息
     * @param roomIds
     * @return
     */
    public List<ArenaRoom> findByIds(List<Long> roomIds) {
        if (CollectionUtils.isEmpty(roomIds)) {
            return new ArrayList<>();
        }

        Criteria[] criterias = new Criteria[roomIds.size()];
        for (int i = 0; i < roomIds.size(); i++) {
            criterias[i] = Criteria.where("_id").is(roomIds.get(i));
        }

        Criteria criteria = new Criteria();
        criteria.orOperator(criterias);
        final List<ArenaRoom> rooms = mongoTemplate.find(new Query(criteria), ArenaRoom.class);
        return rooms;
    }

    /**
     * 根据练习id查询竞技场
     * @param practiceId
     * @return
     */
    public ArenaRoom findByPracticeId(long practiceId){
        final Criteria criteria = Criteria.where("practices").in(practiceId);
        final List<ArenaRoom> rooms = mongoTemplate.find(new Query(criteria), ArenaRoom.class);
        ArenaRoom arenaRoom = null;
        if (rooms != null) {
            arenaRoom = rooms.get(0);
        }

        return arenaRoom;
    }
}
