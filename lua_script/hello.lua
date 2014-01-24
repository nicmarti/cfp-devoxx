local proposals = redis.call("KEYS", "Proposals:Votes:*")
for i = 1, #proposals do
	redis.log(redis.LOG_DEBUG, "proposal i=" .. proposals[i])	

	local uuidAndScores = redis.call("ZRANGE", proposals[i], 0, 10, "WITHSCORES")
	redis.call("HSET", "Computed:Scores", proposals[i], 0)
 	redis.call("HSET", "Computed:Voters", proposals[i], 0)
	
	for j=1,#uuidAndScores,2 do
  		redis.log(redis.LOG_DEBUG, "uuid:"..  uuidAndScores[j] .. " score:" .. uuidAndScores[j + 1])
		redis.call("HINCRBY", "Computed:Scores", proposals[i], uuidAndScores[j + 1])
		redis.call("HINCRBY", "Computed:Voters", proposals[i], 1)
	end
end
return #proposals