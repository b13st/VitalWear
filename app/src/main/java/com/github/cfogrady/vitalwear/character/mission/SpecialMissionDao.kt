package com.github.cfogrady.vitalwear.character.mission

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SpecialMissionDao {
    @Query("select * from special_missions where character_id = :characterId order by slot asc")
    fun getByCharacterId(characterId: Int): List<SpecialMissionEntity>

    @Insert
    fun insertAll(missions: List<SpecialMissionEntity>)

    @Update
    fun updateMany(missions: Collection<SpecialMissionEntity>)

    @Query("delete from special_missions where character_id = :characterId")
    fun deleteByCharacterId(characterId: Int)
}
