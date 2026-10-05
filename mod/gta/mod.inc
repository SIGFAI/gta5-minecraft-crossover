// Minecraft Crossover, GTA side. Two things only GTA can see, reported to Minecraft:
//   "death": a GTA person died near the player -> a zombie rises in Minecraft there.
//   "wreck": a GTA vehicle was destroyed near the player -> Minecraft answers with primed TNT and a creeper.
// And one op Minecraft calls: "mc.cheer" plays a short GTA sound burst at the player.
// Runs inside rehan's script.cpp (anonymous namespace).

	inline BOOL mod_IsEntityDead(Entity e) { return invoke<BOOL>(0x5F9532F3B5CC2551, e, 0); }

	std::unordered_set<Ped> g_reportedDead;
	std::unordered_set<Vehicle> g_reportedWreck;
	int g_nextScan = 0;

	inline void mod_TerminateScript(const char *name) { invoke<Void>(0x9DC711BC69C548DF, name); }

	void mod_connected(Ped player)
	{
		g_reportedDead.clear();
		g_reportedWreck.clear();
	}

	void mod_tick(Ped player)
	{
		const int now = natives::GetGameTimer();
		if (now < g_nextScan)
			return;
		g_nextScan = now + 400;
		const Vector3 me = natives::GetEntityCoords(player, TRUE);
		int peds[256];
		const int n = worldGetAllPeds(peds, 256);
		for (int i = 0; i < n; ++i)
		{
			const Ped p = peds[i];
			if (p == player || g_doublePeds.count(p) || g_reportedDead.count(p) || !natives::IsPedDeadOrDying(p))
				continue;
			const Vector3 at = natives::GetEntityCoords(p, FALSE);
			const float dx = at.x - me.x, dy = at.y - me.y;
			if (dx * dx + dy * dy > 60.0f * 60.0f)
				continue;
			g_reportedDead.insert(p);
			char mc[96];
			xo_to_mc_json(at, mc, sizeof(mc));
			xo_emit("death", "\"mc\":%s", mc);
		}
		int vehs[128];
		const int nv = worldGetAllVehicles(vehs, 128);
		for (int i = 0; i < nv; ++i)
		{
			const Vehicle v = vehs[i];
			if (g_reportedWreck.count(v))
				continue;
			if (!(mod_IsEntityDead(v) || natives::IsEntityOnFire(v)))
				continue;
			const Vector3 at = natives::GetEntityCoords(v, FALSE);
			const float dx = at.x - me.x, dy = at.y - me.y;
			if (dx * dx + dy * dy > 70.0f * 70.0f)
				continue;
			g_reportedWreck.insert(v);
			char mc[96];
			xo_to_mc_json(at, mc, sizeof(mc));
			xo_emit("wreck", "\"mc\":%s", mc);
		}
		if (g_reportedDead.size() > 512)
			g_reportedDead.clear();
		if (g_reportedWreck.size() > 256)
			g_reportedWreck.clear();
	}

	bool mod_op(Ped player, const std::string &op, const std::string &m)
	{
		(void)player;
		(void)m;
		if (op == "mc.skip")
			return true; // kept for old demos: the kit's story gate skips the opening mission by itself
		if (op != "mc.cheer")
			return false;
		xo_sound("PICK_UP", "HUD_FRONTEND_DEFAULT_SOUNDSET", nullptr);
		return true;
	}
