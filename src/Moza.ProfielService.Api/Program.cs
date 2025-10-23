using System.Text;
using System.Text.Json.Serialization;

using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Protocols.Configuration;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;

using Moza.ProfielService.Api.Configuration;
using Moza.ProfielService.Api.Data;
using Moza.ProfielService.Api.Data.Repositories;
using Moza.ProfielService.Api.External.Clients;
using Moza.ProfielService.Api.Services;

using AppJsonSerializerContext = Moza.ProfielService.Api.Common.Serialization.AppJsonSerializerContext;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.UseKestrel(options => options.AddServerHeader = false);
builder.Host.UseDefaultServiceProvider(options => options.ValidateScopes = true);

var authSection = builder.Configuration.GetSection("Authentication");
var authSettings = authSection.Get<AuthenticationSettings>() ?? throw new InvalidConfigurationException("Authentication settings not found");

builder.Services.Configure<AuthenticationSettings>(authSection);

// Authentication with JWT Bearer, note hier moet een echte secret key komen
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        var secretKey = authSettings.SecretKey;
        var key = Encoding.ASCII.GetBytes(secretKey!);

        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = false,
            ValidateAudience = false,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(key)
        };
    });

builder.Services.AddAuthorization();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(options =>
{
    options.SwaggerDoc("v1", new OpenApiInfo { Title = "Profiel Service API", Version = "v1" });

    options.AddSecurityDefinition("Bearer",
        new OpenApiSecurityScheme
        {
            Description = "Paste your JWT token below. 'Bearer' will be added automatically.",
            Name = "Authorization",
            In = ParameterLocation.Header,
            Type = SecuritySchemeType.Http,
            Scheme = "bearer",
            BearerFormat = "JWT"
        });

    options.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        { new OpenApiSecurityScheme { Reference = new OpenApiReference { Type = ReferenceType.SecurityScheme, Id = "Bearer" } }, [] }
    });
});

builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
    });

builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.TypeInfoResolverChain.Insert(0, AppJsonSerializerContext.Default);
});

builder.Services.AddDbContext<ProfielDbContext>(options =>
{
    var connectionString = builder.Configuration.GetConnectionString("ProfielDb");
    options.UseNpgsql(connectionString, innerOptions =>
    {
        innerOptions.MigrationsAssembly(typeof(Program).Assembly.GetName().Name);
        innerOptions.UseQuerySplittingBehavior(QuerySplittingBehavior.SplitQuery);
    });
});

builder.Services.AddAutoMapper(typeof(Program));
builder.Services.AddScoped<OndernemingService>();
builder.Services.AddScoped<OndernemingRepository>();
builder.Services.AddScoped<AuditLogService>();
builder.Services.AddScoped<AuditLogRepository>();
builder.Services.AddMemoryCache();
builder.Services.AddSingleton<KvkProfielClientService>();
builder.Services.AddSingleton<EmailVerificatieClient>();
builder.Services.AddHttpClient<KvkProfielClient>(client =>
{
    client.BaseAddress = new Uri("https://developers.kvk.nl/test/api/v1/");
});

builder.Services.AddHttpClient<EmailVerificatieClient>(client =>
{
    client.BaseAddress = new Uri("https://verificatie.notifynl.nl/");
});

builder.Services.AddControllers();
builder.Services.AddHttpContextAccessor();

var app = builder.Build();
using var scope = app.Services.CreateScope();
var dbContext = scope.ServiceProvider.GetRequiredService<ProfielDbContext>();
dbContext.Database.Migrate();

app.MapControllers();
app.UseSwagger();
app.UseSwaggerUI();
app.UseAuthentication();
app.UseAuthorization();
app.Run();