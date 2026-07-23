-- ガンブレード用ステートマシン (m870 のシェル単発装填方式を流用)
--
-- リロードの装填ループ回数を「不足弾数」と「拡張マガジンのレベル」から決める:
--   レベル0 (未装着)           … 1発ずつ装填  (reload_push_1)
--   レベル1 (帯状ストリップ)   … 2発ずつ装填  (reload_push_2)
--   レベル2 (円状スピードローダー) … 4発ずつ装填 (reload_push_4)
-- 残弾がある場合は不足分だけループするので、装填動作の回数が減る。
local default = require("tacz_default_state_machine")
local STATIC_TRACK_LINE = default.STATIC_TRACK_LINE
local MAIN_TRACK = default.MAIN_TRACK
local main_track_states = default.main_track_states

local idle_state = setmetatable({}, {__index = main_track_states.idle})

local reload_state = {
    need_ammo = 0,
    loaded_ammo = 0,
    per_push = 1,
    was_empty = false
}

-- idle の transition を上書きして、リロード入力を独自のリロード状態へ振り向ける
function idle_state.transition(this, context, input)
    if (input == INPUT_RELOAD) then
        return this.main_track_states.gunblade_reload
    end
    return main_track_states.idle.transition(this, context, input)
end

function reload_state.entry(this, context)
    local state = this.main_track_states.gunblade_reload
    state.was_empty = (context:getAmmoCount() <= 0)
    state.need_ammo = context:getMaxAmmoCount() - context:getAmmoCount()
    state.loaded_ammo = 0
    local level = context:getMagExtentLevel()
    if (level >= 2) then
        state.per_push = 4
    elseif (level == 1) then
        state.per_push = 2
    else
        state.per_push = 1
    end
    local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
    if (state.was_empty) then
        context:runAnimation("reload_intro_empty", track, false, PLAY_ONCE_HOLD, 0.2)
    else
        context:runAnimation("reload_intro", track, false, PLAY_ONCE_HOLD, 0.2)
    end
end

-- intro が終わったら装填ループ。不足分を per_push 発ずつ詰める
function reload_state.update(this, context)
    local state = this.main_track_states.gunblade_reload
    if (state.loaded_ammo >= state.need_ammo) then
        context:trigger(this.INPUT_RELOAD_RETREAT)
    else
        local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
        if (context:isHolding(track)) then
            local anim = "reload_push_1"
            if (state.per_push == 4) then
                anim = "reload_push_4"
            elseif (state.per_push == 2) then
                anim = "reload_push_2"
            end
            -- 空リロード中は薬莢列が落ちたままのポーズを焼き込んだ _empty 版を使う
            -- (push はトラック上で intro を置き換えるため、保持ポーズも自前で持つ必要がある)
            if (state.was_empty) then
                anim = anim .. "_empty"
            end
            context:runAnimation(anim, track, false, PLAY_ONCE_HOLD, 0)
            state.loaded_ammo = state.loaded_ammo + state.per_push
        end
    end
end

function reload_state.transition(this, context, input)
    if (input == this.INPUT_RELOAD_RETREAT or input == INPUT_CANCEL_RELOAD) then
        local state = this.main_track_states.gunblade_reload
        local track = context:getTrack(STATIC_TRACK_LINE, MAIN_TRACK)
        if (state.was_empty) then
            context:runAnimation("reload_end_empty", track, false, PLAY_ONCE_STOP, 0.2)
        else
            context:runAnimation("reload_end", track, false, PLAY_ONCE_STOP, 0.2)
        end
        return this.main_track_states.idle
    end
    return this.main_track_states.idle.transition(this, context, input)
end

local M = setmetatable({
    main_track_states = setmetatable({
        idle = idle_state,
        gunblade_reload = reload_state
    }, {__index = main_track_states}),
    INPUT_RELOAD_RETREAT = "reload_retreat",
}, {__index = default})

function M:initialize(context)
    default.initialize(self, context)
    self.main_track_states.gunblade_reload.need_ammo = 0
    self.main_track_states.gunblade_reload.loaded_ammo = 0
end

return M
