
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNTurboSerialportSpec.h"

@interface TurboSerialport : NSObject <NativeTurboSerialportSpec>
#else
#import <React/RCTBridgeModule.h>

@interface TurboSerialport : NSObject <RCTBridgeModule>
#endif

@end
