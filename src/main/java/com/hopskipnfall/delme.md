# Exp 1

```kt
Client(id = 0, frameDelay = 1, WIFI, description = "wireless"),
Client(id = 1, frameDelay = 1, WIRED, description = "wired"),
```

```
60000ms Server: laggy: true
60000ms Server: Lagstat:
0 - Drift: -1869ms
1 - Drift: 0ms
60000ms Server: Overall game drift: -2.385535632s
```

Recommendation: p0 to 2 frames

```
60000ms Server: laggy: false
60000ms Server: Lagstat:
0 - Drift: -148ms
1 - Drift: 0ms
60000ms Server: Overall game drift: -352.698938ms
```

Success

# Exp 2

```kt
Client(id = 0, frameDelay = 1, WIRED.copy(mean = 17.milliseconds), description = "wireless"),
Client(id = 1, frameDelay = 1, WIRED.copy(mean = 16.milliseconds), description = "wired"),
```

```
60000ms Server: laggy: true
60000ms Server: Lagstat:
0 - Drift: -2094ms
1 - Drift: 0ms
60000ms Server: Overall game drift: -3.240042270s
```

Recommendation: p0 to 2 frames????

```
60000ms Server: laggy: true
60000ms Server: Lagstat:
0 - Drift: 0ms
1 - Drift: -1027ms
60000ms Server: Overall game drift: -1.027235686s
```

STILL LAGGY. Requires 