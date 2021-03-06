
@stationModal = (el) -> new Vue
  el: el
  mixins: [formatter, startMission]
  data:
    mission: {id: null, mission: {id: null}}
    game: {}
    times: []
    moneys: []
    distances: []
  methods:
    setMission: (mission) ->
      @mission = mission
      @getRankings()
    getRankings: ->
      @getTimes()
      @getMoneys()
      @getDistances()
    getTimes: ->
      API.getJSON "/api/game/#{@mission.mission.id}/ranking/time", (json) =>
        @times = json
    getMoneys: ->
      API.getJSON "/api/game/#{@mission.mission.id}/ranking/money", (json) =>
        @moneys = json
    getDistances: ->
      API.getJSON "/api/game/#{@mission.mission.id}/ranking/distance", (json) =>
        @distances = json
  watch: ->
    'mission.id': ->
      @getTimes()
      @getMoneys()
      @getDistances()
