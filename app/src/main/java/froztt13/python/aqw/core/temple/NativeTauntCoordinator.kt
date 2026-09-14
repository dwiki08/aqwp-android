package froztt13.python.aqw.core.temple

class NativeTauntCoordinator {
    private val taunters = mutableListOf<String>()
    private var currentIndex = 0
    private var lastTauntTime = 0L
    private var lastEventTime = 0L
    private var _activeTaunter: String? = null
    val currentActiveTaunter: String?
        get() = _activeTaunter

    @Synchronized
    fun registerTaunter(username: String) {
        val lower = username.trim().lowercase()
        if (lower.isNotEmpty() && !taunters.contains(lower)) {
            taunters.add(lower)
            taunters.sort()
        }
    }

    @Synchronized
    fun unregisterTaunter(username: String) {
        val lower = username.trim().lowercase()
        taunters.remove(lower)
        if (currentIndex >= taunters.size) {
            currentIndex = 0
        }
    }

    @Synchronized
    fun getActiveTaunter(): String? {
        if (taunters.isEmpty()) {
            _activeTaunter = null
            return null
        }
        val now = System.currentTimeMillis()
        if (_activeTaunter == null || (now - lastTauntTime >= 15000L)) {
            if (_activeTaunter != null) {
                currentIndex = (currentIndex + 1) % taunters.size
            }
            _activeTaunter = taunters[currentIndex]
            lastTauntTime = now
        }
        return _activeTaunter
    }

    @Synchronized
    fun rotateTaunt() {
        if (taunters.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % taunters.size
            _activeTaunter = taunters[currentIndex]
            lastTauntTime = System.currentTimeMillis()
        }
    }

    @Synchronized
    fun skipTaunt(username: String) {
        if (_activeTaunter.equals(username.trim(), ignoreCase = true)) {
            rotateTaunt()
        }
    }

    @Synchronized
    fun requestTaunt(username: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastEventTime < 5000L) {
            return false
        }
        val active = getActiveTaunter()
        if (active != null && active.equals(username.trim(), ignoreCase = true)) {
            lastEventTime = now
            rotateTaunt()
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        taunters.clear()
        currentIndex = 0
        lastTauntTime = 0L
        lastEventTime = 0L
        _activeTaunter = null
    }
}
