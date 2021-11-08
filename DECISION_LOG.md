# Decision Log


## Event Sources

### 1. Move Retries to an Abstract Controller.
 
The original idea was to explicitly support retry in the scheduler. However, this turned out to complicate the algorithm
in case of event sources. Mostly it would be harder to manage the buffer, and the other event sources, thus what
does it mean for other event sources that there was a failed controllerConfiguration execution? Probably it would be better to
manage this in an abstract controllerConfiguration, and just use the "reprocess event-source" source in case of an error. 
        
