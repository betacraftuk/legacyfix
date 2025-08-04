#ifndef LEGACYFIXNATIVE_H
#define LEGACYFIXNATIVE_H

#ifdef _MSC_VER
#define DLLEXPORT __declspec( dllexport )
#else
#define DLLEXPORT __attribute__( ( visibility( "default" ) ) )
#endif //_MSC_VER

#endif //LEGACYFIXNATIVE_H
